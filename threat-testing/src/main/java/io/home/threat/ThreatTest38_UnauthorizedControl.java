package io.home.threat;

import io.home.client.DeviceInfo;
import io.home.client.MqttSecureClient;
import io.home.registry.DeviceIdentityHasher;
import io.home.registry.model.Device;
import io.home.registry.model.SecurityAlert;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * Тест угрозы 38: Несанкционированное управление электроснабжением через MQTT
 * 
 * Описание угрозы:
 * Публикация команд в MQTT топики управления реле и выключателей для
 * несанкционированного включения/отключения питания без физического доступа.
 * 
 * Ожидаемое поведение системы:
 * - DeviceAuthorizatorPolicy запрещает публикацию в /cmd топики от устройств
 * - Только APPROVED устройства могут публиковать в свои топики (telemetry, health, register)
 * - PENDING/BLOCKED устройства не могут публиковать команды
 * - Попытка публикации команды отклоняется
 * - Security alert создается при попытке несанкционированной публикации
 * 
 * Защитные механизмы:
 * - DeviceAuthorizatorPolicy.canWrite() проверяет права на публикацию
 * - Устройства не могут публиковать в /cmd топики
 * - Только APPROVED устройства могут публиковать
 * - Проверка соответствия clientId и топика
 */
public class ThreatTest38_UnauthorizedControl extends ThreatTestBase {
    
    private static final Logger logger = LoggerFactory.getLogger(ThreatTest38_UnauthorizedControl.class);
    
    @Override
    public String getThreatName() {
        return "Несанкционированное управление электроснабжением через MQTT";
    }
    
    @Override
    public int getThreatNumber() {
        return 38;
    }
    
    @Override
    public String getThreatDescription() {
        return "Попытка публикации команд управления в MQTT топики для несанкционированного " +
               "включения/отключения электропитания без физического доступа к устройствам.";
    }
    
    @Override
    public String getExpectedBehavior() {
        return "Публикация команд управления отклоняется для всех устройств. " +
               "Устройства не могут публиковать в /cmd топики. " +
               "Только APPROVED устройства могут публиковать телеметрию в свои топики. " +
               "Security alert создается при попытке несанкционированной публикации.";
    }
    
    @Override
    public boolean execute() throws Exception {
        logger.info("========================================");
        logger.info("Starting Threat Test 38: {}", getThreatName());
        logger.info("========================================");
        
        initialize();
        ensureBrokerStarted();
        
        // Создаем тестовые устройства с разными статусами
        DeviceInfo approvedDevice = new DeviceInfo("IOT-2025-APPROVED", "AA:BB:CC:DD:EE:01", "SMART_PLUG");
        DeviceInfo pendingDevice = new DeviceInfo("IOT-2025-PENDING", "AA:BB:CC:DD:EE:02", "SMART_SWITCH");
        DeviceInfo blockedDevice = new DeviceInfo("IOT-2025-BLOCKED", "AA:BB:CC:DD:EE:03", "SMART_PLUG");
        
        // Создаем устройства в БД
        Device approvedDev = createTestDevice(approvedDevice, "APPROVED");
        Device pendingDev = createTestDevice(pendingDevice, "PENDING");
        Device blockedDev = createTestDevice(blockedDevice, "BLOCKED");
        
        long initialAlerts = alertRepository.findAll().size();
        
        // Тест 1: Попытка публикации команды от APPROVED устройства (должна быть отклонена)
        logger.info("Test 1: Attempting to publish command from APPROVED device");
        boolean test1Passed = testPublishCommandFromApprovedDevice(approvedDevice);
        addResult("Test 1 (APPROVED device publish command): " + (test1Passed ? "PASSED - Command rejected" : "FAILED - Command allowed"));
        
        // Тест 2: Попытка публикации команды от PENDING устройства
        logger.info("Test 2: Attempting to publish command from PENDING device");
        boolean test2Passed = testPublishCommandFromPendingDevice(pendingDevice);
        addResult("Test 2 (PENDING device publish command): " + (test2Passed ? "PASSED - Command rejected" : "FAILED - Command allowed"));
        
        // Тест 3: Попытка публикации команды от BLOCKED устройства
        logger.info("Test 3: Attempting to publish command from BLOCKED device");
        boolean test3Passed = testPublishCommandFromBlockedDevice(blockedDevice);
        addResult("Test 3 (BLOCKED device publish command): " + (test3Passed ? "PASSED - Command rejected" : "FAILED - Command allowed"));
        
        // Тест 4: Попытка публикации в чужой /cmd топик
        logger.info("Test 4: Attempting to publish command to another device's topic");
        boolean test4Passed = testPublishToAnotherDeviceTopic(approvedDevice, pendingDevice);
        addResult("Test 4 (publish to another device topic): " + (test4Passed ? "PASSED - Command rejected" : "FAILED - Command allowed"));
        
        // Тест 5: Проверка что APPROVED устройство может публиковать телеметрию
        logger.info("Test 5: Verifying APPROVED device can publish telemetry");
        boolean test5Passed = testPublishTelemetryFromApprovedDevice(approvedDevice);
        addResult("Test 5 (APPROVED device publish telemetry): " + (test5Passed ? "PASSED - Telemetry allowed" : "FAILED - Telemetry rejected"));
        
        // Проверка alerts
        long finalAlerts = alertRepository.findAll().size();
        boolean alertsCreated = (finalAlerts > initialAlerts);
        addResult("Security alerts created: " + alertsCreated + " (new alerts: " + (finalAlerts - initialAlerts) + ")");
        
        // Очистка тестовых устройств
        try {
            deleteTestDevice(approvedDevice);
            deleteTestDevice(pendingDevice);
            deleteTestDevice(blockedDevice);
            logger.info("Test devices cleaned up");
        } catch (Exception e) {
            logger.warn("Error cleaning up test devices", e);
        }
        
        boolean allTestsPassed = test1Passed && test2Passed && test3Passed && test4Passed && test5Passed;
        
        addResult("Unauthorized control commands blocked: " + allTestsPassed);
        
        printSummary();
        cleanup();
        
        return allTestsPassed;
    }
    
    /**
     * Создание тестового устройства в БД.
     */
    private Device createTestDevice(DeviceInfo deviceInfo, String status) {
        Device device = new Device(
            deviceInfo.getDeviceType(),
            DeviceIdentityHasher.hash(deviceInfo.getSerial()),
            DeviceIdentityHasher.hash(deviceInfo.getMac()),
            DeviceIdentityHasher.hashComposite(deviceInfo.getSerial(), deviceInfo.getMac())
        );
        device.setStatus(status);
        device = deviceRepository.insert(device);
        logger.info("Test device created: {} with status {} (ID: {})", deviceInfo.getSerial(), status, device.getId());
        return device;
    }
    
    /**
     * Удаление тестового устройства из БД.
     */
    private void deleteTestDevice(DeviceInfo deviceInfo) {
        try {
            String compositeHash = DeviceIdentityHasher.hashComposite(deviceInfo.getSerial(), deviceInfo.getMac());
            Optional<Device> deviceOpt = deviceRepository.findByCompositeHash(compositeHash);
            if (deviceOpt.isPresent()) {
                try (Connection conn = dbManager.getConnection();
                     PreparedStatement stmt = conn.prepareStatement("DELETE FROM devices WHERE id = ?")) {
                    stmt.setLong(1, deviceOpt.get().getId());
                    stmt.executeUpdate();
                }
            }
        } catch (Exception e) {
            logger.warn("Error deleting test device: {}", deviceInfo.getSerial(), e);
        }
    }
    
    /**
     * Тест публикации команды от APPROVED устройства.
     * Устройства не должны иметь права публиковать в /cmd топики.
     */
    private boolean testPublishCommandFromApprovedDevice(DeviceInfo deviceInfo) {
        MqttSecureClient client = null;
        try {
            client = new MqttSecureClient.Builder()
                .deviceInfo(deviceInfo)
                .host(DEFAULT_BROKER_HOST)
                .port(DEFAULT_TLS_PORT)
                .clientKeystore(clientKeystorePath, clientKeystorePassword)
                .clientTruststore(clientTruststorePath, clientTruststorePassword)
                .build();
            
            client.connect();
            
            String controllerId = System.getProperty("controller.id", "controller-01");
            String commandTopic = "home/" + controllerId + "/devices/" + deviceInfo.getSerial() + "/cmd";
            String commandPayload = "{\"action\": \"turn_off\", \"device\": \"" + deviceInfo.getSerial() + "\"}";
            
            try {
                // Попытка публикации команды
                client.publish(commandTopic, commandPayload, 1, false);
                
                // Если публикация прошла - это плохо
                logger.error("FAILED: Command publication was allowed from device!");
                return false;
                
            } catch (MqttException e) {
                // Ожидаем MqttException - публикация должна быть отклонена
                logger.info("Expected: Command publication rejected - {}", e.getMessage());
                return true;
            }
            
        } catch (Exception e) {
            logger.error("Unexpected error during test", e);
            return false;
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    logger.warn("Error closing client", e);
                }
            }
        }
    }
    
    /**
     * Тест публикации команды от PENDING устройства.
     */
    private boolean testPublishCommandFromPendingDevice(DeviceInfo deviceInfo) {
        MqttSecureClient client = null;
        try {
            client = new MqttSecureClient.Builder()
                .deviceInfo(deviceInfo)
                .host(DEFAULT_BROKER_HOST)
                .port(DEFAULT_TLS_PORT)
                .clientKeystore(clientKeystorePath, clientKeystorePassword)
                .clientTruststore(clientTruststorePath, clientTruststorePassword)
                .build();
            
            client.connect();
            
            String controllerId = System.getProperty("controller.id", "controller-01");
            String commandTopic = "home/" + controllerId + "/devices/" + deviceInfo.getSerial() + "/cmd";
            String commandPayload = "{\"action\": \"turn_on\"}";
            
            try {
                client.publish(commandTopic, commandPayload, 1, false);
                logger.error("FAILED: Command publication was allowed from PENDING device!");
                return false;
            } catch (MqttException e) {
                logger.info("Expected: Command publication rejected for PENDING device - {}", e.getMessage());
                return true;
            }
            
        } catch (Exception e) {
            logger.error("Unexpected error during test", e);
            return false;
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    logger.warn("Error closing client", e);
                }
            }
        }
    }
    
    /**
     * Тест публикации команды от BLOCKED устройства.
     */
    private boolean testPublishCommandFromBlockedDevice(DeviceInfo deviceInfo) {
        MqttSecureClient client = null;
        try {
            client = new MqttSecureClient.Builder()
                .deviceInfo(deviceInfo)
                .host(DEFAULT_BROKER_HOST)
                .port(DEFAULT_TLS_PORT)
                .clientKeystore(clientKeystorePath, clientKeystorePassword)
                .clientTruststore(clientTruststorePath, clientTruststorePassword)
                .build();
            
            // BLOCKED устройства могут даже не подключиться
            try {
                client.connect();
            } catch (Exception e) {
                logger.info("BLOCKED device connection rejected (expected)");
                return true; // Считаем успехом если подключение отклонено
            }
            
            String controllerId = System.getProperty("controller.id", "controller-01");
            String commandTopic = "home/" + controllerId + "/devices/" + deviceInfo.getSerial() + "/cmd";
            String commandPayload = "{\"action\": \"turn_on\"}";
            
            try {
                client.publish(commandTopic, commandPayload, 1, false);
                logger.error("FAILED: Command publication was allowed from BLOCKED device!");
                return false;
            } catch (MqttException e) {
                logger.info("Expected: Command publication rejected for BLOCKED device - {}", e.getMessage());
                return true;
            }
            
        } catch (Exception e) {
            logger.info("BLOCKED device test completed with exception (expected): {}", e.getClass().getSimpleName());
            return true;
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    logger.warn("Error closing client", e);
                }
            }
        }
    }
    
    /**
     * Тест публикации в чужой /cmd топик.
     */
    private boolean testPublishToAnotherDeviceTopic(DeviceInfo authorizedDevice, DeviceInfo targetDevice) {
        MqttSecureClient client = null;
        try {
            client = new MqttSecureClient.Builder()
                .deviceInfo(authorizedDevice)
                .host(DEFAULT_BROKER_HOST)
                .port(DEFAULT_TLS_PORT)
                .clientKeystore(clientKeystorePath, clientKeystorePassword)
                .clientTruststore(clientTruststorePath, clientTruststorePassword)
                .build();
            
            client.connect();
            
            String controllerId = System.getProperty("controller.id", "controller-01");
            // Пытаемся опубликовать в топик другого устройства
            String commandTopic = "home/" + controllerId + "/devices/" + targetDevice.getSerial() + "/cmd";
            String commandPayload = "{\"action\": \"turn_off\"}";
            
            try {
                client.publish(commandTopic, commandPayload, 1, false);
                logger.error("FAILED: Command publication to another device's topic was allowed!");
                return false;
            } catch (MqttException e) {
                logger.info("Expected: Command publication to another device rejected - {}", e.getMessage());
                return true;
            }
            
        } catch (Exception e) {
            logger.error("Unexpected error during test", e);
            return false;
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    logger.warn("Error closing client", e);
                }
            }
        }
    }
    
    /**
     * Тест что APPROVED устройство может публиковать телеметрию.
     * Это должно быть разрешено.
     */
    private boolean testPublishTelemetryFromApprovedDevice(DeviceInfo deviceInfo) {
        MqttSecureClient client = null;
        try {
            client = new MqttSecureClient.Builder()
                .deviceInfo(deviceInfo)
                .host(DEFAULT_BROKER_HOST)
                .port(DEFAULT_TLS_PORT)
                .clientKeystore(clientKeystorePath, clientKeystorePassword)
                .clientTruststore(clientTruststorePath, clientTruststorePassword)
                .build();
            
            client.connect();
            
            String controllerId = System.getProperty("controller.id", "controller-01");
            String telemetryTopic = "home/" + controllerId + "/devices/" + deviceInfo.getSerial() + "/telemetry";
            String telemetryPayload = "{\"temperature\": 25.5, \"humidity\": 60.0}";
            
            try {
                client.publish(telemetryTopic, telemetryPayload, 0, false);
                logger.info("Successfully published telemetry (expected behavior)");
                return true;
            } catch (MqttException e) {
                logger.error("FAILED: Telemetry publication was rejected - {}", e.getMessage());
                return false;
            }
            
        } catch (Exception e) {
            logger.error("Unexpected error during test", e);
            return false;
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    logger.warn("Error closing client", e);
                }
            }
        }
    }
}

