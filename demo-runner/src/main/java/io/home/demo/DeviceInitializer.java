package io.home.demo;

import io.home.client.DeviceInfo;
import io.home.registry.DatabaseManager;
import io.home.registry.DeviceIdentityHasher;
import io.home.registry.model.Device;
import io.home.registry.model.DeviceConnection;
import io.home.registry.model.SecurityAlert;
import io.home.registry.model.Telemetry;
import io.home.registry.repository.ConnectionRepository;
import io.home.registry.repository.DeviceRepository;
import io.home.registry.repository.SecurityAlertRepository;
import io.home.registry.repository.TelemetryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class DeviceInitializer {
    
    private static final Logger logger = LoggerFactory.getLogger(DeviceInitializer.class);
    
    private final DatabaseManager dbManager;
    private final DeviceRepository deviceRepository;
    private final TelemetryRepository telemetryRepository;
    private final SecurityAlertRepository alertRepository;
    private final ConnectionRepository connectionRepository;
    
    public DeviceInitializer() {
        this.dbManager = new DatabaseManager();
        this.deviceRepository = new DeviceRepository(dbManager);
        this.telemetryRepository = new TelemetryRepository(dbManager);
        this.alertRepository = new SecurityAlertRepository(dbManager);
        this.connectionRepository = new ConnectionRepository(dbManager);
    }
    
    public void initializeDatabase(boolean clearExisting) {
        try {
            if (clearExisting) {
                logger.info("Clearing existing devices...");
                clearAllDevices();
            }
            
            initializeDevices();
            initializeTelemetry();
            initializeAlerts();
            initializeConnections();
            
            logger.info("Database initialization completed successfully");
        } catch (Exception e) {
            logger.error("Error during database initialization: {}", e.getMessage(), e);
        }
    }
    
    public void initializeDemoDevices(boolean clearExisting) {
        initializeDatabase(clearExisting);
    }
    
    private void initializeDevices() {
        logger.info("Initializing demo devices...");
        
        // Generate random MAC addresses for security (not sequential)
        java.util.Random random = new java.util.Random();
        List<DeviceInfo> demoDevices = Arrays.asList(
            new DeviceInfo("IOT-2025-0001", generateRandomMac(random), "TEMP_SENSOR"),
            new DeviceInfo("IOT-2025-0002", generateRandomMac(random), "SMART_PLUG"),
            new DeviceInfo("IOT-2025-0003", generateRandomMac(random), "ENERGY_SENSOR"),
            new DeviceInfo("IOT-2025-0004", generateRandomMac(random), "SMART_SWITCH"),
            new DeviceInfo("IOT-2025-0005", generateRandomMac(random), "TEMP_SENSOR") // Critical device
        );
        
        for (DeviceInfo deviceInfo : demoDevices) {
            createDeviceIfNotExists(deviceInfo);
        }
        
        // Mark device 5 as critical after creation
        markDeviceAsCritical("IOT-2025-0005");
        
        logger.info("Demo devices initialization completed");
    }
    
    private void createDeviceIfNotExists(DeviceInfo deviceInfo) {
        try {
            String serialHash = DeviceIdentityHasher.hash(deviceInfo.getSerial());
            String macHash = DeviceIdentityHasher.hash(deviceInfo.getMac());
            String compositeHash = DeviceIdentityHasher.hashComposite(deviceInfo.getSerial(), deviceInfo.getMac());
            
            Device device = new Device(
                deviceInfo.getDeviceType(),
                serialHash,
                macHash,
                compositeHash
            );
            
            device.setStatus("APPROVED"); // Pre-approved for demo
            device.setApprovedBy("SYSTEM_DEMO");
            
            deviceRepository.upsertIfNotExists(device);
            logger.info("Created/ensured demo device: {} ({})", deviceInfo.getSerial(), deviceInfo.getDeviceType());
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (message.contains("duplicate key")
                || message.contains("уникальность")
                || message.contains("unique")) {
                // Игнорировать - запись уже существует
                logger.debug("Device {} already exists, skipping", deviceInfo.getSerial());
            } else if (message.contains("relation") && message.contains("does not exist")) {
                // Игнорировать - таблица еще не создана
                logger.debug("Table devices does not exist yet, skipping device creation");
            } else {
                logger.error("Error creating device {}: {}", deviceInfo.getSerial(), e.getMessage(), e);
            }
        }
    }
    
    private void markDeviceAsCritical(String serial) {
        try {
            String serialHash = DeviceIdentityHasher.hash(serial);
            Optional<Device> deviceOpt = deviceRepository.findBySerialHash(serialHash);
            if (deviceOpt.isPresent()) {
                Device device = deviceOpt.get();
                deviceRepository.markAsCritical(device.getId());
                logger.info("Marked device {} (ID: {}) as critical", serial, device.getId());
            } else {
                logger.warn("Device with serial {} not found for marking as critical", serial);
            }
        } catch (Exception e) {
            logger.error("Failed to mark device as critical: {}", serial, e);
        }
    }
    
    private void clearAllDevices() {
        try {
            deviceRepository.clearAllDevices();
            logger.info("All devices cleared successfully");
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (message.contains("relation") && message.contains("does not exist")) {
                logger.debug("Tables do not exist yet, nothing to clear");
            } else {
                logger.warn("Error clearing devices: {}", e.getMessage());
            }
            // Don't throw - allow initialization to continue
        }
    }
    
    private String generateRandomMac(java.util.Random random) {
        // Generate random MAC address: XX:XX:XX:XX:XX:XX
        StringBuilder mac = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            if (i > 0) mac.append(":");
            int octet = random.nextInt(256);
            mac.append(String.format("%02X", octet));
        }
        return mac.toString();
    }
    
    private void initializeTelemetry() {
        logger.info("Initializing test telemetry...");
        
        try {
            List<Device> devices = deviceRepository.findAll();
            if (devices.isEmpty()) {
                logger.warn("No devices found, skipping telemetry initialization");
                return;
            }
            
            LocalDateTime now = LocalDateTime.now();
            int telemetryCount = 0;
            
            for (Device device : devices) {
                String controllerId = "demo-controller";
                String topic = String.format("home/%s/devices/%s/telemetry", controllerId, device.getSerialHash());
                
                // Create test telemetry entries based on device type
                if (device.getDeviceType().equals("TEMP_SENSOR")) {
                    // Temperature sensor data
                    for (int i = 0; i < 5; i++) {
                        LocalDateTime ts = now.minusMinutes(10 - i * 2);
                        String payload = String.format("{\"temperature\": %.1f, \"humidity\": %.1f}", 
                                22.5 + (i * 0.3), 45.0 + (i * 0.5));
                        Telemetry telemetry = new Telemetry(
                            device.getId(),
                            topic,
                            ts,
                            "temperature",
                            22.5 + (i * 0.3),
                            payload
                        );
                        telemetry.setReceivedAt(ts);
                        createTelemetryIfNotExists(telemetry);
                        telemetryCount++;
                    }
                } else if (device.getDeviceType().equals("ENERGY_SENSOR")) {
                    // Energy sensor data
                    for (int i = 0; i < 5; i++) {
                        LocalDateTime ts = now.minusMinutes(10 - i * 2);
                        String payload = String.format("{\"power\": %.2f, \"voltage\": %.1f}", 
                                125.5 + (i * 0.8), 220.0 + (i * 0.2));
                        Telemetry telemetry = new Telemetry(
                            device.getId(),
                            topic,
                            ts,
                            "power",
                            125.5 + (i * 0.8),
                            payload
                        );
                        telemetry.setReceivedAt(ts);
                        createTelemetryIfNotExists(telemetry);
                        telemetryCount++;
                    }
                } else if (device.getDeviceType().equals("SMART_PLUG") || device.getDeviceType().equals("SMART_SWITCH")) {
                    // Smart plug/switch data
                    for (int i = 0; i < 3; i++) {
                        LocalDateTime ts = now.minusMinutes(10 - i * 3);
                        String payload = String.format("{\"state\": \"%s\", \"power\": %.2f}", 
                                (i % 2 == 0 ? "ON" : "OFF"), 50.0 + (i * 2.5));
                        Telemetry telemetry = new Telemetry(
                            device.getId(),
                            topic,
                            ts,
                            "state",
                            (double)(i % 2),
                            payload
                        );
                        telemetry.setReceivedAt(ts);
                        createTelemetryIfNotExists(telemetry);
                        telemetryCount++;
                    }
                }
            }
            
            logger.info("Initialized {} test telemetry records", telemetryCount);
            
        } catch (Exception e) {
            logger.error("Failed to initialize test telemetry", e);
        }
    }
    
    private void createTelemetryIfNotExists(Telemetry telemetry) {
        try {
            telemetryRepository.insert(telemetry);
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (message.contains("duplicate key")
                || message.contains("уникальность")
                || message.contains("unique")) {
                // Игнорировать - запись уже существует
                logger.debug("Telemetry record already exists, skipping");
            } else if (message.contains("relation") && message.contains("does not exist")) {
                // Игнорировать - таблица еще не создана
                logger.debug("Table telemetry does not exist yet, skipping");
            } else {
                logger.error("Error creating telemetry record: {}", e.getMessage(), e);
            }
        }
    }
    
    private void initializeAlerts() {
        logger.info("Initializing test security alerts...");
        
        try {
            List<Device> devices = deviceRepository.findAll();
            if (devices.isEmpty()) {
                logger.warn("No devices found, skipping alerts initialization");
                return;
            }
            
            int alertCount = 0;
            String[] alertTypes = {
                "DEVICE_REGISTRATION",
                "DEVICE_RECONNECTION",
                "HEALTH_CHECK_ERROR"
            };
            
            // Create alerts for each device
            for (Device device : devices) {
                String serialHash = device.getSerialHash();
                
                // Create one alert of each type
                for (String alertType : alertTypes) {
                    String details = String.format("Test alert for device %s - Type: %s", 
                            device.getDeviceType(), alertType);
                    SecurityAlert alert = new SecurityAlert(alertType, serialHash, details);
                    createAlertIfNotExists(alert);
                    alertCount++;
                }
            }
            
            logger.info("Initialized {} test security alerts", alertCount);
            
        } catch (Exception e) {
            logger.error("Failed to initialize test alerts", e);
        }
    }
    
    private void createAlertIfNotExists(SecurityAlert alert) {
        try {
            alertRepository.createAlert(alert);
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (message.contains("duplicate key")
                || message.contains("уникальность")
                || message.contains("unique")) {
                // Игнорировать - запись уже существует
                logger.debug("Security alert already exists, skipping");
            } else if (message.contains("relation") && message.contains("does not exist")) {
                // Игнорировать - таблица еще не создана
                logger.debug("Table security_alerts does not exist yet, skipping");
            } else {
                logger.error("Error creating security alert: {}", e.getMessage(), e);
            }
        }
    }
    
    private void initializeConnections() {
        logger.info("Initializing test device connections...");
        
        try {
            List<Device> devices = deviceRepository.findAll();
            if (devices.isEmpty()) {
                logger.warn("No devices found, skipping connections initialization");
                return;
            }
            
            int connectionCount = 0;
            
            // Create active and inactive connections for each device
            for (Device device : devices) {
                // Active connection
                DeviceConnection activeConn = new DeviceConnection(
                    device.getId(),
                    "192.168.1." + (100 + device.getId().intValue()),
                    "MQTT Client v3.1.1"
                );
                createConnectionIfNotExists(activeConn, true);
                connectionCount++;
                
                // Create inactive connection (create first, then close it)
                DeviceConnection inactiveConn = new DeviceConnection(
                    device.getId(),
                    "192.168.1." + (200 + device.getId().intValue()),
                    "MQTT Client v3.1.0"
                );
                Long connectionId = createConnectionIfNotExists(inactiveConn, false);
                if (connectionId != null) {
                    // Close it immediately to make it inactive
                    try {
                        connectionRepository.closeConnection(connectionId);
                    } catch (Exception e) {
                        logger.debug("Error closing connection {}: {}", connectionId, e.getMessage());
                    }
                }
                connectionCount++;
            }
            
            logger.info("Initialized {} test device connections ({} active, {} inactive)", 
                    connectionCount, devices.size(), devices.size());
            
        } catch (Exception e) {
            logger.error("Failed to initialize test connections", e);
        }
    }
    
    private Long createConnectionIfNotExists(DeviceConnection connection, boolean isActive) {
        try {
            DeviceConnection created = connectionRepository.createConnection(connection);
            return created.getId();
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (message.contains("duplicate key")
                || message.contains("уникальность")
                || message.contains("unique")) {
                // Игнорировать - запись уже существует
                logger.debug("Device connection already exists, skipping");
                return null;
            } else if (message.contains("relation") && message.contains("does not exist")) {
                // Игнорировать - таблица еще не создана
                logger.debug("Table device_connections does not exist yet, skipping");
                return null;
            } else {
                logger.error("Error creating device connection: {}", e.getMessage(), e);
                return null;
            }
        }
    }
    
    public void close() {
        if (dbManager != null) {
            try {
                dbManager.close();
            } catch (Exception e) {
                logger.error("Error closing database manager", e);
            }
        }
    }
}
