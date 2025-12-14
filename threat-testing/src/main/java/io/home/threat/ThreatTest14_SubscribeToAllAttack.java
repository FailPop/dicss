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

/**
 * Тест угрозы 14: Атака "subscribe to all" на MQTT-брокер
 * 
 * Описание угрозы:
 * Попытка подписки на топик "#" (все топики) в MQTT-брокере для получения
 * всей информации о состоянии системы при отсутствии ограничений доступа.
 * 
 * Ожидаемое поведение системы:
 * - DeviceAuthorizatorPolicy должен запретить подписку на wildcard "#"
 * - Только ADMIN клиенты могут подписываться на "#"
 * - IOT устройства не могут подписаться на "#"
 * - Попытка подписки отклоняется брокером
 * 
 * Защитные механизмы:
 * - DeviceAuthorizatorPolicy.canRead() проверяет wildcard подписки
 * - Только клиенты с префиксом "ADMIN_" могут подписываться на "#"
 * - IOT устройства ограничены только своими топиками
 */
public class ThreatTest14_SubscribeToAllAttack extends ThreatTestBase {
    
    private static final Logger logger = LoggerFactory.getLogger(ThreatTest14_SubscribeToAllAttack.class);
    
    @Override
    public String getThreatName() {
        return "Атака 'subscribe to all' на MQTT-брокер";
    }
    
    @Override
    public int getThreatNumber() {
        return 14;
    }
    
    @Override
    public String getThreatDescription() {
        return "Попытка подписки на топик '#' (все топики) для получения всей информации " +
               "о состоянии системы и перехвата всех сообщений между устройствами.";
    }
    
    @Override
    public String getExpectedBehavior() {
        return "Подписка на wildcard '#' отклоняется для IOT устройств. " +
               "Только ADMIN клиенты могут подписываться на все топики. " +
               "IOT устройства ограничены только своими топиками.";
    }
    
    @Override
    public boolean execute() throws Exception {
        logger.info("========================================");
        logger.info("Starting Threat Test 14: {}", getThreatName());
        logger.info("========================================");
        
        initialize();
        ensureBrokerStarted();
        
        // Создаем тестовое устройство
        DeviceInfo testDevice = new DeviceInfo("IOT-2025-TEST", "AA:BB:CC:DD:EE:FF", "TEMP_SENSOR");
        String compositeHash = DeviceIdentityHasher.hashComposite(testDevice.getSerial(), testDevice.getMac());
        
        // Создаем устройство в БД со статусом APPROVED для теста
        Device device = new Device(
            testDevice.getDeviceType(),
            DeviceIdentityHasher.hash(testDevice.getSerial()),
            DeviceIdentityHasher.hash(testDevice.getMac()),
            compositeHash
        );
        device.setStatus("APPROVED");
        deviceRepository.insert(device);
        logger.info("Test device created: {}", testDevice.getSerial());
        
        long initialAlerts = alertRepository.findAll().size();
        
        // Тест 1: Попытка подписки на "#" от имени IOT устройства
        logger.info("Test 1: Attempting to subscribe to '#' as IOT device");
        boolean test1Passed = testSubscribeToAllAsIotDevice(testDevice);
        addResult("Test 1 (IOT device subscribe to #): " + (test1Passed ? "PASSED - Subscription rejected" : "FAILED - Subscription allowed"));
        
        // Тест 2: Попытка подписки на "home/+/#" (все топики в home)
        logger.info("Test 2: Attempting to subscribe to 'home/+/#' as IOT device");
        boolean test2Passed = testSubscribeToHomeWildcard(testDevice);
        addResult("Test 2 (IOT device subscribe to home/+/#): " + (test2Passed ? "PASSED - Subscription rejected" : "FAILED - Subscription allowed"));
        
        // Тест 3: Проверка что устройство может подписаться на свой собственный топик
        logger.info("Test 3: Verifying device can subscribe to its own command topic");
        boolean test3Passed = testSubscribeToOwnTopic(testDevice);
        addResult("Test 3 (subscribe to own topic): " + (test3Passed ? "PASSED - Subscription allowed" : "FAILED - Subscription rejected"));
        
        // Проверка alerts
        long finalAlerts = alertRepository.findAll().size();
        boolean alertsCreated = (finalAlerts > initialAlerts);
        addResult("Security alerts created: " + alertsCreated + " (new alerts: " + (finalAlerts - initialAlerts) + ")");
        
        // Очистка тестового устройства
        try {
            Optional<Device> deviceToDelete = deviceRepository.findByCompositeHash(compositeHash);
            if (deviceToDelete.isPresent()) {
                // Удаляем только тестовое устройство через SQL
                try (java.sql.Connection conn = dbManager.getConnection();
                     java.sql.PreparedStatement stmt = conn.prepareStatement("DELETE FROM devices WHERE id = ?")) {
                    stmt.setLong(1, deviceToDelete.get().getId());
                    stmt.executeUpdate();
                    logger.info("Test device cleaned up");
                }
            }
        } catch (Exception e) {
            logger.warn("Error cleaning up test device", e);
        }
        
        boolean allTestsPassed = test1Passed && test2Passed && test3Passed;
        
        addResult("Wildcard subscription attacks blocked: " + allTestsPassed);
        
        printSummary();
        cleanup();
        
        return allTestsPassed;
    }
    
    /**
     * Тест подписки на "#" от имени IOT устройства.
     * Ожидаем что подписка будет отклонена.
     */
    private boolean testSubscribeToAllAsIotDevice(DeviceInfo deviceInfo) {
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
            logger.info("Connected as IOT device: {}", client.getDeviceInfo().getSerial());
            
            // Попытка подписки на "#"
            CountDownLatch messageReceived = new CountDownLatch(1);
            try {
                client.subscribe("#", (topic, payload) -> {
                    logger.warn("Received message on wildcard subscription - this should not happen!");
                    messageReceived.countDown();
                });
                
                // Если подписка прошла успешно - это плохо
                logger.error("FAILED: Subscription to '#' was allowed for IOT device!");
                return false;
                
            } catch (MqttException e) {
                // Ожидаем MqttException - подписка должна быть отклонена
                logger.info("Expected: Subscription to '#' rejected - {}", e.getMessage());
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
     * Тест подписки на "home/+/#" от имени IOT устройства.
     */
    private boolean testSubscribeToHomeWildcard(DeviceInfo deviceInfo) {
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
            
            // Попытка подписки на "home/+/#"
            try {
                client.subscribe("home/+/#", (topic, payload) -> {
                    logger.warn("Received message on home wildcard - this should not happen!");
                });
                
                logger.error("FAILED: Subscription to 'home/+/#' was allowed for IOT device!");
                return false;
                
            } catch (MqttException e) {
                logger.info("Expected: Subscription to 'home/+/#' rejected - {}", e.getMessage());
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
     * Тест что устройство может подписаться на свой собственный топик команд.
     * Это должно быть разрешено.
     */
    private boolean testSubscribeToOwnTopic(DeviceInfo deviceInfo) {
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
            String ownCommandTopic = "home/" + controllerId + "/devices/" + deviceInfo.getSerial() + "/cmd";
            
            // Попытка подписки на свой топик команд
            try {
                client.subscribe(ownCommandTopic, (topic, payload) -> {
                    logger.info("Received command on own topic (expected)");
                });
                
                logger.info("Successfully subscribed to own command topic (expected behavior)");
                return true;
                
            } catch (MqttException e) {
                logger.error("FAILED: Subscription to own topic was rejected - {}", e.getMessage());
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

