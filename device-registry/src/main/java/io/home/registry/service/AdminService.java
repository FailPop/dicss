package io.home.registry.service;

import io.home.registry.DatabaseManager;
import io.home.registry.model.Device;
import io.home.registry.model.SecurityAlert;
import io.home.registry.repository.ConnectionRepository;
import io.home.registry.repository.DeviceRepository;
import io.home.registry.repository.SecurityAlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class AdminService {
    
    private static final Logger logger = LoggerFactory.getLogger(AdminService.class);
    
    private DeviceRepository deviceRepository;
    private ConnectionRepository connectionRepository;
    private SecurityAlertRepository alertRepository;
    
    // No-arg constructor for CDI proxying
    public AdminService() {
        // CDI will use the parameterized constructor through producer
    }
    
    public AdminService(DatabaseManager dbManager) {
        this.deviceRepository = new DeviceRepository(dbManager);
        this.connectionRepository = new ConnectionRepository(dbManager);
        this.alertRepository = new SecurityAlertRepository(dbManager);
    }
    
    public boolean approveDevice(Long deviceId, String adminName) {
        try {
            Optional<Device> deviceOpt = deviceRepository.findById(deviceId);
            
            if (deviceOpt.isEmpty()) {
                logger.warn("Device not found for approval: {}", deviceId);
                return false;
            }
            
            Device device = deviceOpt.get();
            deviceRepository.updateStatus(deviceId, "APPROVED", adminName);
            
            // Create audit log
            SecurityAlert auditAlert = new SecurityAlert(
                "DEVICE_APPROVED", 
                device.getSerialHash(),
                String.format("{\"admin\": \"%s\", \"device_id\": %d, \"action\": \"APPROVED\"}", adminName, deviceId)
            );
            alertRepository.createAlert(auditAlert);
            
            logger.info("Device {} approved by admin: {}", deviceId, adminName);
            return true;
            
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument approving device: {}", deviceId, e);
            return false;
        } catch (RuntimeException e) {
            logger.error("Runtime error approving device: {} - {}", deviceId, e.getClass().getSimpleName(), e);
            return false;
        } catch (Exception e) {
            logger.error("Unexpected error approving device: {}", deviceId, e);
            return false;
        }
    }
    
    public boolean rejectDevice(Long deviceId, String adminName, String reason) {
        try {
            Optional<Device> deviceOpt = deviceRepository.findById(deviceId);
            
            if (deviceOpt.isEmpty()) {
                logger.warn("Device not found for rejection: {}", deviceId);
                return false;
            }
            
            Device device = deviceOpt.get();
            deviceRepository.updateStatus(deviceId, "REJECTED", adminName);
            
            // Close any active connections
            connectionRepository.closeAllConnectionsForDevice(deviceId);
            
            // Create audit log
            SecurityAlert auditAlert = new SecurityAlert(
                "DEVICE_REJECTED", 
                device.getSerialHash(),
                String.format("{\"admin\": \"%s\", \"device_id\": %d, \"reason\": \"%s\", \"action\": \"REJECTED\"}", 
                    adminName, deviceId, reason)
            );
            alertRepository.createAlert(auditAlert);
            
            logger.info("Device {} rejected by admin: {} - Reason: {}", deviceId, adminName, reason);
            return true;
            
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument rejecting device: {}", deviceId, e);
            return false;
        } catch (RuntimeException e) {
            logger.error("Runtime error rejecting device: {} - {}", deviceId, e.getClass().getSimpleName(), e);
            return false;
        } catch (Exception e) {
            logger.error("Unexpected error rejecting device: {}", deviceId, e);
            return false;
        }
    }
    
    public boolean unblockDevice(Long deviceId, String adminName) {
        try {
            Optional<Device> deviceOpt = deviceRepository.findById(deviceId);
            
            if (deviceOpt.isEmpty()) {
                logger.warn("Device not found for unblocking: {}", deviceId);
                return false;
            }
            
            Device device = deviceOpt.get();
            deviceRepository.updateStatus(deviceId, "APPROVED", adminName);
            
            // Create audit log
            SecurityAlert auditAlert = new SecurityAlert(
                "DEVICE_UNBLOCKED", 
                device.getSerialHash(),
                String.format("{\"admin\": \"%s\", \"device_id\": %d, \"action\": \"UNBLOCKED\"}", adminName, deviceId)
            );
            alertRepository.createAlert(auditAlert);
            
            logger.info("Device {} unblocked by admin: {}", deviceId, adminName);
            return true;
            
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument unblocking device: {}", deviceId, e);
            return false;
        } catch (RuntimeException e) {
            logger.error("Runtime error unblocking device: {} - {}", deviceId, e.getClass().getSimpleName(), e);
            return false;
        } catch (Exception e) {
            logger.error("Unexpected error unblocking device: {}", deviceId, e);
            return false;
        }
    }
    
    public boolean markDeviceAsCritical(Long deviceId, String adminName) {
        try {
            Optional<Device> deviceOpt = deviceRepository.findById(deviceId);
            
            if (deviceOpt.isEmpty()) {
                logger.warn("Device not found for marking as critical: {}", deviceId);
                return false;
            }
            
            Device device = deviceOpt.get();
            deviceRepository.markAsCritical(deviceId);
            
            // Create audit log
            SecurityAlert auditAlert = new SecurityAlert(
                "DEVICE_MARKED_CRITICAL", 
                device.getSerialHash(),
                String.format("{\"admin\": \"%s\", \"device_id\": %d, \"action\": \"MARKED_CRITICAL\"}", adminName, deviceId)
            );
            alertRepository.createAlert(auditAlert);
            
            logger.info("Device {} marked as critical by admin: {}", deviceId, adminName);
            return true;
            
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument marking device as critical: {}", deviceId, e);
            return false;
        } catch (RuntimeException e) {
            logger.error("Runtime error marking device as critical: {} - {}", deviceId, e.getClass().getSimpleName(), e);
            return false;
        } catch (Exception e) {
            logger.error("Unexpected error marking device as critical: {}", deviceId, e);
            return false;
        }
    }
    
    public List<Device> getPendingDevices() {
        return deviceRepository.findByStatus("PENDING");
    }
    
    public List<Device> getAllDevices() {
        return deviceRepository.findAll();
    }
    
    public List<SecurityAlert> getAllAlerts() {
        return alertRepository.findAll();
    }
    
    public List<SecurityAlert> getAlertsByDevice(String deviceSerialHash) {
        return alertRepository.findByDeviceSerialHash(deviceSerialHash);
    }
}

