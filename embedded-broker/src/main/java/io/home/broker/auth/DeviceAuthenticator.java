package io.home.broker.auth;

import io.home.client.DeviceInfo;
import io.home.registry.DeviceIdentityHasher;
import io.home.registry.DatabaseManager;
import io.home.registry.model.Device;
import io.home.registry.model.DeviceConnection;
import io.home.registry.model.SecurityAlert;
import io.home.registry.repository.ConnectionRepository;
import io.home.registry.repository.DeviceRepository;
import io.home.registry.repository.SecurityAlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class DeviceAuthenticator {
    
    private static final Logger logger = LoggerFactory.getLogger(DeviceAuthenticator.class);
    
    private final DatabaseManager dbManager;
    private final DeviceRepository deviceRepository;
    private final ConnectionRepository connectionRepository;
    private final SecurityAlertRepository alertRepository;
    
    public DeviceAuthenticator(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        this.deviceRepository = new DeviceRepository(dbManager);
        this.connectionRepository = new ConnectionRepository(dbManager);
        this.alertRepository = new SecurityAlertRepository(dbManager);
    }

    public DatabaseManager getDbManager() {
        return dbManager;
    }
    
    public DeviceInfo parseClientId(String clientId) {
        if (clientId == null || !clientId.startsWith("IOT")) {
            throw new IllegalArgumentException("Invalid clientId format. Expected: IOT{serial}{mac}");
        }
        
        // Extract serial and MAC prefix from clientId format: IOT{last4digits}{first6mac}
        String serialSuffix = clientId.substring(3, 7); // Last 4 digits of serial
        String macPrefix = clientId.substring(7, 13); // First 6 chars of MAC
        
        // Reconstruct full serial
        String serial = "IOT-2025-" + serialSuffix;
        
        // Format MAC prefix: AA:BB:CC from AABBCC
        String macPrefixFormatted = macPrefix.substring(0, 2) + ":" + 
                   macPrefix.substring(2, 4) + ":" + 
                   macPrefix.substring(4, 6);
        
        // Find device by serial (MAC will be matched from database after registration)
        Optional<Device> deviceOpt = deviceRepository.findBySerialHash(DeviceIdentityHasher.hash(serial));
        
        if (deviceOpt.isPresent()) {
            Device device = deviceOpt.get();
            // Try to find device with matching MAC prefix in connections
            // For now, use placeholder MAC that will be matched during registration
            String fullMac = macPrefixFormatted + ":00:00:00";
            
            // Check if we can find device with this serial and any MAC starting with prefix
            // This is a workaround: during registration, device will send full MAC
            return new DeviceInfo(serial, fullMac, device.getDeviceType());
        }
        
        // Device not found yet - return with placeholder MAC (device will register with full MAC)
        return new DeviceInfo(serial, macPrefixFormatted + ":00:00:00", "TEMP_SENSOR");
    }
    
    public DeviceValidationResult validateDevice(DeviceInfo deviceInfo) {
        String compositeHash = DeviceIdentityHasher.hashComposite(deviceInfo.getSerial(), deviceInfo.getMac());
        
        Optional<Device> deviceOpt = deviceRepository.findByCompositeHash(compositeHash);
        
        if (deviceOpt.isEmpty()) {
            logger.warn("Device not found in registry: serial={}, mac={}", deviceInfo.getSerial(), deviceInfo.getMac());
            return DeviceValidationResult.NOT_FOUND;
        }
        
        Device device = deviceOpt.get();
        
        if ("BLOCKED".equals(device.getStatus())) {
            logger.warn("Device is blocked: {}", deviceInfo.getSerial());
            return DeviceValidationResult.BLOCKED;
        }
        
        if ("PENDING".equals(device.getStatus())) {
            logger.warn("Device is pending approval: {}", deviceInfo.getSerial());
            return DeviceValidationResult.PENDING;
        }
        
        if (!"APPROVED".equals(device.getStatus())) {
            logger.warn("Device has invalid status: {} - {}", deviceInfo.getSerial(), device.getStatus());
            return DeviceValidationResult.INVALID_STATUS;
        }
        
        return DeviceValidationResult.VALID;
    }
    
    public DuplicateConnectionResult checkDuplicateConnection(String compositeHash) {
        Optional<Device> deviceOpt = deviceRepository.findByCompositeHash(compositeHash);
        
        if (deviceOpt.isEmpty()) {
            return DuplicateConnectionResult.NO_DEVICE;
        }
        
        Device device = deviceOpt.get();
        Optional<DeviceConnection> activeConnectionOpt = connectionRepository.findActiveByDeviceId(device.getId());
        
        if (activeConnectionOpt.isEmpty()) {
            return DuplicateConnectionResult.NO_DUPLICATE;
        }
        
        DeviceConnection activeConnection = activeConnectionOpt.get();
        return new DuplicateConnectionResult(true, device, activeConnection);
    }
    
    public void handleDuplicateDetection(Device device, DeviceConnection oldConnection, String newIP) {
        String oldIP = oldConnection.getIpAddress();
        
        logger.warn("Duplicate connection detected for device: {} (ID: {})", device.getSerialHash(), device.getId());
        logger.warn("Old IP: {}, New IP: {}", oldIP, newIP);
        
        String alertType;
        String actionTaken;
        
        if (oldIP.equals(newIP)) {
            // Same IP - probably legitimate reconnection
            logger.info("Same IP detected, allowing reconnection for device: {}", device.getSerialHash());
            connectionRepository.closeConnection(oldConnection.getId());
            alertType = "DEVICE_RECONNECTION";
            actionTaken = "CLOSED_OLD_ALLOWED_NEW";
        } else {
            // Different IPs - potential clone
            if (device.isCritical()) {
                // Critical device - keep old connection, reject new
                logger.error("CRITICAL device clone attempt detected! Keeping old connection active.");
                alertType = "CRITICAL_DEVICE_CLONE_ATTEMPT";
                actionTaken = "REJECTED_NEW_KEPT_OLD";
            } else {
                // Non-critical device - disconnect both
                logger.error("Non-critical device clone detected! Disconnecting both connections.");
                connectionRepository.closeConnection(oldConnection.getId());
                deviceRepository.updateStatus(device.getId(), "BLOCKED", "SYSTEM");
                alertType = "DEVICE_CLONE_DETECTED";
                actionTaken = "BLOCKED_DEVICE_DISCONNECTED_BOTH";
            }
        }
        
        // Create security alert
        String details = String.format(
            "{\"old_ip\": \"%s\", \"new_ip\": \"%s\", \"device_critical\": %s, \"action_taken\": \"%s\", \"old_connection_time\": \"%s\"}",
            oldIP, newIP, device.isCritical(), actionTaken, oldConnection.getConnectedAt()
        );
        
        SecurityAlert alert = new SecurityAlert(alertType, device.getSerialHash(), details);
        alertRepository.createAlert(alert);
        
        logger.info("Security alert created: {} for device: {}", alertType, device.getSerialHash());
    }
    
    public DeviceRepository getDeviceRepository() {
        return deviceRepository;
    }
    
    public ConnectionRepository getConnectionRepository() {
        return connectionRepository;
    }
    
    public SecurityAlertRepository getAlertRepository() {
        return alertRepository;
    }
    
    public enum DeviceValidationResult {
        VALID, NOT_FOUND, BLOCKED, PENDING, INVALID_STATUS
    }
    
    public static class DuplicateConnectionResult {
        private final boolean hasDuplicate;
        private final Device device;
        private final DeviceConnection connection;
        
        public DuplicateConnectionResult(boolean hasDuplicate, Device device, DeviceConnection connection) {
            this.hasDuplicate = hasDuplicate;
            this.device = device;
            this.connection = connection;
        }
        
        public static DuplicateConnectionResult NO_DEVICE = new DuplicateConnectionResult(false, null, null);
        public static DuplicateConnectionResult NO_DUPLICATE = new DuplicateConnectionResult(false, null, null);
        
        public boolean hasDuplicate() { return hasDuplicate; }
        public Device getDevice() { return device; }
        public DeviceConnection getConnection() { return connection; }
    }
}
