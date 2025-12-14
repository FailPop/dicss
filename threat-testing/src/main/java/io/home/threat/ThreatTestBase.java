package io.home.threat;

import io.home.controller.core.BrokerService;
import io.home.registry.DatabaseManager;
import io.home.registry.repository.ConnectionRepository;
import io.home.registry.repository.DeviceRepository;
import io.home.registry.repository.SecurityAlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Базовый класс для тестирования угроз безопасности.
 * Предоставляет общую функциональность для всех тестов угроз.
 */
public abstract class ThreatTestBase {
    
    protected static final Logger logger = LoggerFactory.getLogger(ThreatTestBase.class);
    
    protected static final String DEFAULT_BROKER_HOST = "localhost";
    protected static final int DEFAULT_TLS_PORT = 8884;
    protected static final int DEFAULT_PLAINTEXT_PORT = 1883;
    
    protected String serverKeystorePath;
    protected String serverKeystorePassword;
    protected String brokerTruststorePath;
    protected String brokerTruststorePassword;
    protected String clientKeystorePath;
    protected String clientKeystorePassword;
    protected String clientTruststorePath;
    protected String clientTruststorePassword;
    
    protected DatabaseManager dbManager;
    protected DeviceRepository deviceRepository;
    protected ConnectionRepository connectionRepository;
    protected SecurityAlertRepository alertRepository;
    
    protected BrokerService brokerService;
    protected boolean brokerStartedByTest = false;
    
    protected List<String> testResults = new ArrayList<>();
    
    /**
     * Инициализация теста с параметрами из system properties или дефолтными значениями.
     */
    public void initialize() {
        serverKeystorePath = System.getProperty("server.keystore", "server-keystore.p12");
        serverKeystorePassword = System.getProperty("server.keystore.password", "changeit");
        brokerTruststorePath = System.getProperty("broker.truststore", "broker-truststore.p12");
        brokerTruststorePassword = System.getProperty("broker.truststore.password", "changeit");
        
        clientKeystorePath = System.getProperty("client.keystore", "client.p12");
        clientKeystorePassword = System.getProperty("client.keystore.password", "changeit");
        clientTruststorePath = System.getProperty("client.truststore", "client-truststore.p12");
        clientTruststorePassword = System.getProperty("client.truststore.password", "changeit");
        
        // Использовать тестовую БД если указана
        String testDbUrl = System.getProperty("test.db.url", System.getProperty("db.url", "jdbc:postgresql://localhost:5432/mqtt"));
        System.setProperty("db.url", testDbUrl);
        
        dbManager = new DatabaseManager();
        deviceRepository = new DeviceRepository(dbManager);
        connectionRepository = new ConnectionRepository(dbManager);
        alertRepository = new SecurityAlertRepository(dbManager);
        
        brokerService = BrokerService.getInstance();
        
        logger.info("Threat test initialized: {}", getThreatName());
    }
    
    /**
     * Запуск брокера если он еще не запущен.
     */
    protected void ensureBrokerStarted() throws Exception {
        if (!brokerService.isStarted()) {
            brokerService.start(
                serverKeystorePath,
                serverKeystorePassword,
                brokerTruststorePath,
                brokerTruststorePassword
            );
            brokerStartedByTest = true;
            Thread.sleep(3000); // Дать время брокеру запуститься
            logger.info("Broker started for threat test");
        }
    }
    
    /**
     * Очистка после теста.
     */
    public void cleanup() {
        if (brokerStartedByTest && brokerService.isStarted()) {
            try {
                brokerService.stop();
                logger.info("Broker stopped after threat test");
            } catch (Exception e) {
                logger.error("Error stopping broker", e);
            }
        }
        
        if (dbManager != null) {
            try {
                dbManager.close();
            } catch (Exception e) {
                logger.error("Error closing database manager", e);
            }
        }
    }
    
    /**
     * Выполнение теста угрозы.
     * @return true если тест прошел успешно (защита сработала), false если защита не сработала
     */
    public abstract boolean execute() throws Exception;
    
    /**
     * Получить название угрозы для логирования.
     */
    public abstract String getThreatName();
    
    /**
     * Получить номер угрозы из модели угроз.
     */
    public abstract int getThreatNumber();
    
    /**
     * Получить описание угрозы.
     */
    public abstract String getThreatDescription();
    
    /**
     * Получить ожидаемое поведение системы.
     */
    public abstract String getExpectedBehavior();
    
    /**
     * Добавить результат проверки.
     */
    protected void addResult(String result) {
        testResults.add(result);
        logger.info("[TEST RESULT] {}", result);
    }
    
    /**
     * Получить все результаты теста.
     */
    public List<String> getTestResults() {
        return new ArrayList<>(testResults);
    }
    
    /**
     * Печать сводки результатов теста.
     */
    public void printSummary() {
        logger.info("========================================");
        logger.info("Threat Test Summary: {}", getThreatName());
        logger.info("Threat Number: {}", getThreatNumber());
        logger.info("Description: {}", getThreatDescription());
        logger.info("Expected Behavior: {}", getExpectedBehavior());
        logger.info("Results:");
        for (String result : testResults) {
            logger.info("  - {}", result);
        }
        logger.info("========================================");
    }
}

