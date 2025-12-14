package io.home.broker.interceptor;

import io.home.broker.auth.DeviceAuthenticator;
import io.home.client.DeviceInfo;
import io.home.registry.DeviceIdentityHasher;
import io.home.registry.model.Device;
import io.home.registry.model.DeviceConnection;
import io.home.registry.model.SecurityAlert;
import io.moquette.interception.AbstractInterceptHandler;
import io.moquette.interception.messages.InterceptConnectMessage;
import io.moquette.interception.messages.InterceptDisconnectMessage;
import io.moquette.interception.messages.InterceptPublishMessage;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

public class DeviceInterceptor extends AbstractInterceptHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(DeviceInterceptor.class);
    
    private final DeviceAuthenticator authenticator;
    private final io.home.broker.ingest.TelemetryIngestService telemetryIngestService;
    
    public DeviceInterceptor(DeviceAuthenticator authenticator) {
        this.authenticator = authenticator;
        this.telemetryIngestService = new io.home.broker.ingest.TelemetryIngestService(authenticator.getDbManager());
    }
    
    @Override
    public String getID() {
        return "DeviceInterceptor";
    }
    
    @Override
    public void onConnect(InterceptConnectMessage msg) {
        String clientId = msg.getClientID();
        String clientIP = extractClientIP(msg);
        
        logger.info("Device connection attempt: clientId={}, ip={}", clientId, clientIP);
        
        try {
            // Parse client ID to extract device info (serial from clientId)
            DeviceInfo deviceInfo = authenticator.parseClientId(clientId);
            
            // Find device by serial (MAC may be incomplete in clientId, will be updated during registration)
            String serialHash = DeviceIdentityHasher.hash(deviceInfo.getSerial());
            Optional<Device> deviceOpt = authenticator.getDeviceRepository().findBySerialHash(serialHash);
            
            if (deviceOpt.isEmpty()) {
                logger.debug("Device not yet registered, will register when device sends registration message: {}", deviceInfo.getSerial());
                // Allow connection - device will register via /register topic
                return;
            }
            
            Device device = deviceOpt.get();
            
            // Validate device status
            if ("BLOCKED".equals(device.getStatus())) {
                logger.warn("Device is blocked: {}", deviceInfo.getSerial());
                return;
            }
            
            // Check for duplicate connections using device ID (not compositeHash since MAC may not match yet)
            Optional<DeviceConnection> activeConnectionOpt = 
                authenticator.getConnectionRepository().findActiveByDeviceId(device.getId());
            
            if (activeConnectionOpt.isPresent()) {
                DeviceConnection oldConnection = activeConnectionOpt.get();
                logger.warn("Duplicate connection detected for device: {}", deviceInfo.getSerial());
                authenticator.handleDuplicateDetection(device, oldConnection, clientIP);
                return;
            }
            
            // Register new connection
            DeviceConnection connection = new DeviceConnection(device.getId(), clientIP, clientId);
            authenticator.getConnectionRepository().createConnection(connection);
            logger.info("Device connection registered: {} from IP: {}", deviceInfo.getSerial(), clientIP);
            
        } catch (Exception e) {
            logger.error("Error processing device connection: {}", clientId, e);
            
            // Create security alert for connection errors
            SecurityAlert alert = new SecurityAlert("CONNECTION_ERROR", clientId, 
                "{\"error\": \"" + e.getMessage() + "\", \"client_id\": \"" + clientId + "\"}");
            authenticator.getAlertRepository().createAlert(alert);
        }
    }
    
    @Override
    public void onDisconnect(InterceptDisconnectMessage msg) {
        String clientId = msg.getClientID();
        logger.info("Device disconnection: clientId={}", clientId);
        
        try {
            // Find and close the connection by serial (MAC in clientId may be incomplete)
            DeviceInfo deviceInfo = authenticator.parseClientId(clientId);
            String serialHash = DeviceIdentityHasher.hash(deviceInfo.getSerial());
            
            Optional<Device> deviceOpt = authenticator.getDeviceRepository().findBySerialHash(serialHash);
            if (deviceOpt.isPresent()) {
                Device device = deviceOpt.get();
                Optional<DeviceConnection> activeConnectionOpt = 
                    authenticator.getConnectionRepository().findActiveByDeviceId(device.getId());
                
                if (activeConnectionOpt.isPresent()) {
                    DeviceConnection connection = activeConnectionOpt.get();
                    authenticator.getConnectionRepository().closeConnection(connection.getId());
                    logger.info("Device connection closed: {}", deviceInfo.getSerial());
                }
            }
            
        } catch (Exception e) {
            logger.error("Error processing device disconnection: {}", clientId, e);
        }
    }
    
    @Override
    public void onSessionLoopError(Throwable error) {
        logger.error("Session loop error in device interceptor", error);
    }
    
    // Executor for asynchronous message processing
    private static final java.util.concurrent.ExecutorService messageExecutor = 
        java.util.concurrent.Executors.newFixedThreadPool(
            Integer.parseInt(System.getProperty("broker.message.threads", "10")),
            r -> {
                Thread t = new Thread(r, "mqtt-message-processor");
                t.setDaemon(true);
                return t;
            }
        );
    
    public void onMessagePublished(InterceptPublishMessage msg) {
        String topic = msg.getTopicName();
        
        // Process messages asynchronously to avoid blocking broker
        messageExecutor.submit(() -> {
            try {
                // Monitor health check messages (prod path)
                if (topic.contains("/health") && topic.startsWith("home/")) {
                    logger.debug("Health check message received on topic: {}", topic);
                    
                    try {
                        String payload = msg.getPayload().toString(StandardCharsets.UTF_8);
                        processHealthCheckMessage(topic, payload);
                    } catch (Exception e) {
                        logger.error("Error processing health check message", e);
                    }
                }
                
                // Monitor registration messages (prod path)
                if (topic.endsWith("/register") && topic.startsWith("home/")) {
                    logger.info("Device registration message received");
                    
                    try {
                        String payload = msg.getPayload().toString(StandardCharsets.UTF_8);
                        processRegistrationMessage(payload);
                    } catch (Exception e) {
                        logger.error("Error processing registration message", e);
                    }
                }

                // Telemetry ingest: home/<controllerId>/devices/<deviceId>/telemetry
                if (topic.startsWith("home/") && topic.contains("/devices/") && topic.endsWith("/telemetry")) {
                    try {
                        byte[] payloadBytes = new byte[msg.getPayload().readableBytes()];
                        msg.getPayload().getBytes(0, payloadBytes);
                        telemetryIngestService.ingest(topic, payloadBytes);
                    } catch (Exception e) {
                        logger.error("Error ingesting telemetry for topic {}", topic, e);
                    }
                }
            } catch (Exception e) {
                logger.error("Error in async message processing for topic: {}", topic, e);
            }
        });
    }
    
    private void processHealthCheckMessage(String topic, String payload) {
        // Extract serial from topic: home/<controllerId>/devices/<serial>/health
        String[] parts = topic.split("/");
        String serial = parts.length >= 5 ? parts[3] : "unknown";

        try {
            logger.debug("Health check from device: {}, payload: {}", serial, payload);

            // Parse JSON payload
            JSONObject healthData = new JSONObject(payload);
            
            String mac = healthData.getString("mac");
            String timestampStr = healthData.getString("timestamp");
            int batteryLevel = healthData.optInt("battery_level", -1);
            long uptime = healthData.optLong("uptime", -1);

            // Validate MAC format
            if (!mac.matches("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$")) {
                logger.warn("Invalid MAC format in health check from device: {} - {}", serial, mac);
                createHealthCheckAlert(serial, "INVALID_MAC_FORMAT", 
                    String.format("{\"mac\": \"%s\", \"reason\": \"Invalid MAC format\"}", mac));
                return;
            }

            // Find device by serial
            Optional<Device> deviceOpt = authenticator.getDeviceRepository().findByCompositeHash(
                DeviceIdentityHasher.hashComposite(serial, mac)
            );

            if (deviceOpt.isEmpty()) {
                logger.warn("Device not found for health check: {}", serial);
                createHealthCheckAlert(serial, "DEVICE_NOT_FOUND", 
                    String.format("{\"serial\": \"%s\", \"mac\": \"%s\"}", serial, mac));
                return;
            }

            Device device = deviceOpt.get();

            // Validate MAC matches registered device
            String registeredMacHash = device.getMacHash();
            String receivedMacHash = DeviceIdentityHasher.hash(mac);
            
            if (!registeredMacHash.equals(receivedMacHash)) {
                logger.warn("MAC mismatch in health check from device: {} - registered: {}, received: {}", 
                    serial, registeredMacHash, receivedMacHash);
                createHealthCheckAlert(serial, "MAC_MISMATCH", 
                    String.format("{\"registered_mac_hash\": \"%s\", \"received_mac_hash\": \"%s\"}", 
                        registeredMacHash, receivedMacHash));
                return;
            }

            // Check timestamp for time synchronization
            try {
                LocalDateTime deviceTime = LocalDateTime.parse(timestampStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                LocalDateTime serverTime = LocalDateTime.now();
                
                long timeDiffMinutes = Math.abs(java.time.Duration.between(deviceTime, serverTime).toMinutes());
                
                if (timeDiffMinutes > 5) { // Allow 5 minutes drift
                    logger.warn("Time drift detected for device: {} - {} minutes", serial, timeDiffMinutes);
                    createHealthCheckAlert(serial, "TIME_DRIFT", 
                        String.format("{\"device_time\": \"%s\", \"server_time\": \"%s\", \"drift_minutes\": %d}", 
                            timestampStr, serverTime.toString(), timeDiffMinutes));
                }
            } catch (DateTimeParseException e) {
                logger.warn("Invalid timestamp format in health check from device: {} - {}", serial, timestampStr);
                createHealthCheckAlert(serial, "INVALID_TIMESTAMP", 
                    String.format("{\"timestamp\": \"%s\", \"reason\": \"Invalid timestamp format\"}", timestampStr));
            }

            // Security: Validate device is not blocked and has active connection
            if ("BLOCKED".equals(device.getStatus())) {
                logger.warn("Health check rejected from blocked device: {}", serial);
                createHealthCheckAlert(serial, "HEALTH_CHECK_REJECTED_BLOCKED", 
                    String.format("{\"serial\": \"%s\", \"status\": \"BLOCKED\", \"reason\": \"Device is blocked\"}", serial));
                return;
            }
            
            // Security: Verify device has active connection (prevent spoofing)
            Optional<DeviceConnection> activeConnectionOpt = 
                authenticator.getConnectionRepository().findActiveByDeviceId(device.getId());
            
            if (activeConnectionOpt.isEmpty()) {
                logger.warn("Health check rejected - no active connection for device: {}", serial);
                createHealthCheckAlert(serial, "HEALTH_CHECK_REJECTED_NO_CONNECTION", 
                    String.format("{\"serial\": \"%s\", \"reason\": \"No active MQTT connection\"}", serial));
                return;
            }
            
            // Update last health check only if device is approved and has active connection
            if (!"APPROVED".equals(device.getStatus())) {
                logger.debug("Health check from non-approved device {} (status: {}) - not updating health check timestamp", 
                    serial, device.getStatus());
            } else {
                authenticator.getDeviceRepository().updateLastHealthCheck(device.getId());
            }
            
            logger.debug("Health check processed successfully for device: {} (MAC: {}, Battery: {}%, Uptime: {}s)", 
                serial, mac, batteryLevel, uptime);

        } catch (Exception e) {
            logger.error("Error processing health check for device: {}", serial, e);
            createHealthCheckAlert(serial, "HEALTH_CHECK_ERROR", 
                String.format("{\"error\": \"%s\"}", e.getMessage()));
        }
    }
    
    private void processRegistrationMessage(String payload) {
        try {
            logger.info("Device registration payload: {}", payload);

            // Parse JSON payload
            JSONObject registrationData = new JSONObject(payload);
            
            String serial = registrationData.getString("serial");
            String mac = registrationData.getString("mac");
            String deviceType = registrationData.getString("device_type");
            String firmwareVersion = registrationData.optString("firmware_version", "unknown");
            String hardwareVersion = registrationData.optString("hardware_version", "unknown");

            // Validate required fields
            if (serial == null || serial.trim().isEmpty()) {
                throw new IllegalArgumentException("Serial number is required");
            }
            if (mac == null || mac.trim().isEmpty()) {
                throw new IllegalArgumentException("MAC address is required");
            }
            if (deviceType == null || deviceType.trim().isEmpty()) {
                throw new IllegalArgumentException("Device type is required");
            }

            // Validate MAC format
            if (!mac.matches("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$")) {
                throw new IllegalArgumentException("Invalid MAC address format: " + mac);
            }

            // Validate device type
            if (!isValidDeviceType(deviceType)) {
                throw new IllegalArgumentException("Invalid device type: " + deviceType);
            }

            // Create device record
            String serialHash = DeviceIdentityHasher.hash(serial);
            String macHash = DeviceIdentityHasher.hash(mac);
            String compositeHash = DeviceIdentityHasher.hashComposite(serial, mac);

            // Check if device already exists
            Optional<Device> existingDevice = authenticator.getDeviceRepository().findByCompositeHash(compositeHash);
            
            if (existingDevice.isPresent()) {
                logger.info("Device already registered: {} - updating info", serial);
                Device device = existingDevice.get();
                
                // Update device info if needed
                if (!device.getDeviceType().equals(deviceType)) {
                    logger.info("Device type changed for {}: {} -> {}", serial, device.getDeviceType(), deviceType);
                }
                
                return;
            }

            // Create new device record
            Device newDevice = new Device(deviceType, serialHash, macHash, compositeHash);
            // Auto-approve devices that match pre-registered demo devices (for demo/testing)
            // In production, devices should start as PENDING
            Optional<Device> preRegistered = authenticator.getDeviceRepository().findBySerialHash(serialHash);
            if (preRegistered.isPresent() && "APPROVED".equals(preRegistered.get().getStatus())) {
                newDevice.setStatus("APPROVED");
                logger.info("Device {} auto-approved (pre-registered)", serial);
            } else {
                newDevice.setStatus("PENDING");
            }
            
            authenticator.getDeviceRepository().insert(newDevice);

            // Create registration alert
            String details = String.format(
                "{\"serial\": \"%s\", \"mac\": \"%s\", \"device_type\": \"%s\", \"firmware_version\": \"%s\", \"hardware_version\": \"%s\"}",
                serial, mac, deviceType, firmwareVersion, hardwareVersion
            );
            
            SecurityAlert alert = new SecurityAlert("DEVICE_REGISTRATION", serialHash, details);
            authenticator.getAlertRepository().createAlert(alert);

            logger.info("New device registered: {} ({}) - MAC: {}", serial, deviceType, mac);

        } catch (Exception e) {
            logger.error("Error processing registration message", e);
            
            // Create error alert
            SecurityAlert errorAlert = new SecurityAlert("REGISTRATION_ERROR", "unknown",
                String.format("{\"error\": \"%s\", \"payload\": \"%s\"}", e.getMessage(), payload));
            authenticator.getAlertRepository().createAlert(errorAlert);
        }
    }
    
    private String extractClientIP(InterceptConnectMessage msg) {
        // Moquette doesn't directly provide client IP in InterceptConnectMessage
        // This would need to be implemented differently, possibly through custom authentication
        // For now, return a placeholder
        return "unknown";
    }
    
    private boolean isValidDeviceType(String deviceType) {
        return "TEMP_SENSOR".equals(deviceType) ||
               "SMART_PLUG".equals(deviceType) ||
               "ENERGY_SENSOR".equals(deviceType) ||
               "SMART_SWITCH".equals(deviceType);
    }
    
    private void createHealthCheckAlert(String serial, String alertType, String details) {
        try {
            String serialHash = DeviceIdentityHasher.hash(serial);
            SecurityAlert alert = new SecurityAlert(alertType, serialHash, details);
            authenticator.getAlertRepository().createAlert(alert);
            logger.warn("Health check alert created: {} for device: {}", alertType, serial);
        } catch (Exception e) {
            logger.error("Failed to create health check alert", e);
        }
    }
}