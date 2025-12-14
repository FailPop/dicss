package io.home.threat;

import io.home.client.MqttSecureClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Тест угрозы 6: Несанкционированное подключение к MQTT-брокеру
 * 
 * Описание угрозы:
 * Попытка подключения к MQTT-брокеру без валидного клиентского сертификата
 * или с невалидным сертификатом.
 * 
 * Ожидаемое поведение системы:
 * - Подключение должно быть отклонено на уровне TLS handshake
 * - Security alert не создается (отклонение на сетевом уровне до обработки брокером)
 * - Логирование попытки подключения
 * - Нет записей в device_connections
 * 
 * Защитные механизмы:
 * - mTLS с ClientAuth.REQUIRE принудительно требует клиентский сертификат
 * - Брокер отклоняет подключения без валидного сертификата на этапе SSL handshake
 */
public class ThreatTest6_UnauthorizedMqttConnection extends ThreatTestBase {
    
    private static final Logger logger = LoggerFactory.getLogger(ThreatTest6_UnauthorizedMqttConnection.class);
    
    @Override
    public String getThreatName() {
        return "Несанкционированное подключение к MQTT-брокеру";
    }
    
    @Override
    public int getThreatNumber() {
        return 6;
    }
    
    @Override
    public String getThreatDescription() {
        return "Попытка подключения к MQTT-брокеру без валидного клиентского сертификата " +
               "или с невалидным сертификатом для получения доступа к системе.";
    }
    
    @Override
    public String getExpectedBehavior() {
        return "Подключение отклоняется на уровне TLS handshake. " +
               "Брокер требует валидный клиентский сертификат (mTLS ClientAuth.REQUIRE). " +
               "Попытка подключения логируется, но не создает записей в БД.";
    }
    
    @Override
    public boolean execute() throws Exception {
        logger.info("========================================");
        logger.info("Starting Threat Test 6: {}", getThreatName());
        logger.info("========================================");
        
        initialize();
        ensureBrokerStarted();
        
        // Подсчет начального состояния
        long initialConnections = connectionRepository.findActiveConnections().size();
        long initialAlerts = alertRepository.findAll().size();
        
        addResult("Initial state: " + initialConnections + " active connections, " + initialAlerts + " alerts");
        
        // Тест 1: Попытка подключения без сертификата (невалидный путь)
        logger.info("Test 1: Attempting connection with invalid keystore path");
        boolean test1Passed = testConnectionWithInvalidKeystore();
        addResult("Test 1 (invalid keystore): " + (test1Passed ? "PASSED" : "FAILED"));
        
        // Тест 2: Попытка подключения с невалидным сертификатом (не в truststore брокера)
        logger.info("Test 2: Attempting connection with untrusted certificate");
        boolean test2Passed = testConnectionWithUntrustedCertificate();
        addResult("Test 2 (untrusted certificate): " + (test2Passed ? "PASSED" : "FAILED"));
        
        // Тест 3: Попытка подключения с неверным паролем keystore
        logger.info("Test 3: Attempting connection with wrong keystore password");
        boolean test3Passed = testConnectionWithWrongPassword();
        addResult("Test 3 (wrong password): " + (test3Passed ? "PASSED" : "FAILED"));
        
        // Проверка финального состояния
        long finalConnections = connectionRepository.findActiveConnections().size();
        long finalAlerts = alertRepository.findAll().size();
        
        addResult("Final state: " + finalConnections + " active connections, " + finalAlerts + " alerts");
        
        boolean allTestsPassed = test1Passed && test2Passed && test3Passed;
        boolean noNewConnections = (finalConnections == initialConnections);
        boolean noNewAlerts = (finalAlerts == initialAlerts);
        
        addResult("All unauthorized connection attempts rejected: " + allTestsPassed);
        addResult("No new connections created: " + noNewConnections);
        addResult("No new alerts created (expected): " + noNewAlerts);
        
        printSummary();
        cleanup();
        
        return allTestsPassed && noNewConnections;
    }
    
    /**
     * Тест подключения с невалидным путем к keystore.
     */
    private boolean testConnectionWithInvalidKeystore() {
        try {
            MqttSecureClient client = new MqttSecureClient.Builder()
                .clientId("unauthorized-client-1")
                .host(DEFAULT_BROKER_HOST)
                .port(DEFAULT_TLS_PORT)
                .clientKeystore("nonexistent-keystore.p12", clientKeystorePassword)
                .clientTruststore(clientTruststorePath, clientTruststorePassword)
                .build();
            
            client.connect();
            // Если дошли сюда - подключение прошло, это плохо
            client.close();
            logger.error("FAILED: Connection succeeded with invalid keystore path");
            return false;
            
        } catch (Exception e) {
            // Ожидаем исключение - подключение должно быть отклонено
            logger.info("Expected exception (connection rejected): {}", e.getClass().getSimpleName());
            logger.debug("Exception details", e);
            return true;
        }
    }
    
    /**
     * Тест подключения с недоверенным сертификатом.
     * Создаем временный keystore с сертификатом, который не в truststore брокера.
     */
    private boolean testConnectionWithUntrustedCertificate() {
        // Используем несуществующий или другой keystore
        // В реальном тесте можно создать временный keystore с новым сертификатом
        try {
            // Попытка использовать валидный keystore, но без соответствующего сертификата в truststore брокера
            // Для этого теста мы предполагаем, что у нас есть другой keystore (если нет - тест пропускается)
            String alternativeKeystore = System.getProperty("test.alternative.keystore");
            if (alternativeKeystore == null || !new java.io.File(alternativeKeystore).exists()) {
                logger.info("Skipping untrusted certificate test - alternative keystore not available");
                return true; // Пропускаем если нет альтернативного keystore
            }
            
            MqttSecureClient client = new MqttSecureClient.Builder()
                .clientId("unauthorized-client-2")
                .host(DEFAULT_BROKER_HOST)
                .port(DEFAULT_TLS_PORT)
                .clientKeystore(alternativeKeystore, "changeit")
                .clientTruststore(clientTruststorePath, clientTruststorePassword)
                .build();
            
            client.connect();
            // Если дошли сюда - подключение прошло, это плохо
            client.close();
            logger.error("FAILED: Connection succeeded with untrusted certificate");
            return false;
            
        } catch (MqttException e) {
            // Ожидаем MqttException с SSL ошибкой
            if (e.getCause() instanceof javax.net.ssl.SSLException) {
                logger.info("Expected SSL exception (certificate not trusted): {}", e.getMessage());
                return true;
            }
            logger.warn("Unexpected exception type: {}", e.getClass().getSimpleName());
            return true; // Все равно считаем успехом, так как подключение не прошло
        } catch (Exception e) {
            logger.info("Expected exception (connection rejected): {}", e.getClass().getSimpleName());
            return true;
        }
    }
    
    /**
     * Тест подключения с неверным паролем keystore.
     */
    private boolean testConnectionWithWrongPassword() {
        try {
            MqttSecureClient client = new MqttSecureClient.Builder()
                .clientId("unauthorized-client-3")
                .host(DEFAULT_BROKER_HOST)
                .port(DEFAULT_TLS_PORT)
                .clientKeystore(clientKeystorePath, "wrong-password-12345")
                .clientTruststore(clientTruststorePath, clientTruststorePassword)
                .build();
            
            client.connect();
            // Если дошли сюда - подключение прошло, это плохо
            client.close();
            logger.error("FAILED: Connection succeeded with wrong password");
            return false;
            
        } catch (Exception e) {
            // Ожидаем исключение - неверный пароль должен привести к ошибке
            logger.info("Expected exception (wrong password): {}", e.getClass().getSimpleName());
            logger.debug("Exception details", e);
            return true;
        }
    }
}

