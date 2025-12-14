package io.home.registry.service;

import io.home.registry.DatabaseManager;
import io.home.registry.model.Device;
import io.home.registry.model.SecurityAlert;
import io.home.registry.repository.ConnectionRepository;
import io.home.registry.repository.DeviceRepository;
import io.home.registry.repository.SecurityAlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HealthCheckService {
    
    private static final Logger logger = LoggerFactory.getLogger(HealthCheckService.class);
    
    private final DeviceRepository deviceRepository;
    private final ConnectionRepository connectionRepository;
    private final SecurityAlertRepository alertRepository;
    private final ScheduledExecutorService scheduler;
    
    private static final int HEALTH_CHECK_INTERVAL_MINUTES = 2;
    private static final int DEVICE_OFFLINE_THRESHOLD_MINUTES = 3;
    
    public HealthCheckService(DatabaseManager dbManager) {
        this.deviceRepository = new DeviceRepository(dbManager);
        this.connectionRepository = new ConnectionRepository(dbManager);
        this.alertRepository = new SecurityAlertRepository(dbManager);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "health-check-monitor");
            t.setDaemon(true);
            return t;
        });
    }
    
    public void startMonitoring() {
        logger.info("Starting health check monitoring service (interval: {} minutes)", HEALTH_CHECK_INTERVAL_MINUTES);
        
        scheduler.scheduleAtFixedRate(() -> {
            try {
                checkDeviceHealth();
            } catch (Exception e) {
                logger.error("Error in health check monitoring", e);
            }
        }, HEALTH_CHECK_INTERVAL_MINUTES, HEALTH_CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }
    
    public void stopMonitoring() {
        logger.info("Stopping health check monitoring service");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    private void checkDeviceHealth() {
        logger.debug("Performing health check scan");
        
        List<Device> allDevices = deviceRepository.findAll();
        LocalDateTime now = LocalDateTime.now();
        
        for (Device device : allDevices) {
            try {
                checkSingleDeviceHealth(device, now);
            } catch (Exception e) {
                logger.error("Error checking health for device: {}", device.getId(), e);
            }
        }
    }
    
    private void checkSingleDeviceHealth(Device device, LocalDateTime now) {
        LocalDateTime lastHealthCheck = device.getLastHealthCheck();
        
        if (lastHealthCheck == null) {
            // Device never sent health check
            if (isDeviceRecentlyConnected(device.getId())) {
                logger.debug("Device {} has no health check but recently connected - skipping", device.getId());
                return;
            }
            
            // Create offline alert
            createOfflineAlert(device, "No health check received since registration");
            return;
        }
        
        long minutesSinceLastCheck = ChronoUnit.MINUTES.between(lastHealthCheck, now);
        
        if (minutesSinceLastCheck > DEVICE_OFFLINE_THRESHOLD_MINUTES) {
            // Device is offline
            if (isDeviceRecentlyConnected(device.getId())) {
                logger.debug("Device {} offline but recently connected - skipping alert", device.getId());
                return;
            }
            
            createOfflineAlert(device, String.format("No health check for %d minutes", minutesSinceLastCheck));
            
            // Close any active connections for offline devices
            connectionRepository.closeAllConnectionsForDevice(device.getId());
            logger.info("Closed connections for offline device: {}", device.getId());
        }
    }
    
    private boolean isDeviceRecentlyConnected(Long deviceId) {
        return connectionRepository.findActiveByDeviceId(deviceId).isPresent();
    }
    
    private void createOfflineAlert(Device device, String reason) {
        SecurityAlert alert = new SecurityAlert(
            "DEVICE_OFFLINE",
            device.getSerialHash(),
            String.format("{\"device_id\": %d, \"reason\": \"%s\", \"last_health_check\": \"%s\"}", 
                device.getId(), reason, device.getLastHealthCheck())
        );
        
        alertRepository.createAlert(alert);
        logger.warn("Device offline alert created for device: {} - {}", device.getId(), reason);
    }
    
    public void updateDeviceHealthCheck(Long deviceId) {
        deviceRepository.updateLastHealthCheck(deviceId);
    }
}

