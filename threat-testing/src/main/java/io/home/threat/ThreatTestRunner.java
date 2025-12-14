package io.home.threat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Запуск всех тестов угроз безопасности.
 * Выполняет тесты последовательно и выводит сводный отчет.
 */
public class ThreatTestRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(ThreatTestRunner.class);
    
    public static void main(String[] args) {
        logger.info("========================================");
        logger.info("DICSS Threat Testing Suite");
        logger.info("========================================");
        logger.info("");
        
        List<ThreatTestBase> tests = new ArrayList<>();
        List<TestResult> results = new ArrayList<>();
        
        // Создаем тесты в порядке приоритета
        tests.add(new ThreatTest6_UnauthorizedMqttConnection());
        tests.add(new ThreatTest12_PlaintextPortDisabled());
        tests.add(new ThreatTest14_SubscribeToAllAttack());
        tests.add(new ThreatTest30_MqttDataLeakage());
        tests.add(new ThreatTest38_UnauthorizedControl());
        
        logger.info("Total tests to run: {}", tests.size());
        logger.info("");
        
        // Выполняем тесты последовательно
        for (int i = 0; i < tests.size(); i++) {
            ThreatTestBase test = tests.get(i);
            logger.info("");
            logger.info("========================================");
            logger.info("Running Test {}/{}: Threat {} - {}", 
                i + 1, tests.size(), test.getThreatNumber(), test.getThreatName());
            logger.info("========================================");
            
            try {
                boolean passed = test.execute();
                results.add(new TestResult(test, passed, null));
                
                if (passed) {
                    logger.info("TEST PASSED: Threat {} - {}", test.getThreatNumber(), test.getThreatName());
                } else {
                    logger.warn("TEST FAILED: Threat {} - {}", test.getThreatNumber(), test.getThreatName());
                }
                
            } catch (Exception e) {
                logger.error("TEST ERROR: Threat {} - {}", test.getThreatNumber(), test.getThreatName(), e);
                results.add(new TestResult(test, false, e));
            }
            
            // Пауза между тестами
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // Выводим сводный отчет
        printSummary(results);
    }
    
    private static void printSummary(List<TestResult> results) {
        logger.info("");
        logger.info("========================================");
        logger.info("THREAT TESTING SUMMARY");
        logger.info("========================================");
        logger.info("");
        
        int passed = 0;
        int failed = 0;
        int errors = 0;
        
        for (TestResult result : results) {
            ThreatTestBase test = result.test;
            logger.info("Threat {}: {} - {}", 
                test.getThreatNumber(), 
                test.getThreatName(),
                result.passed ? "PASSED" : (result.error != null ? "ERROR" : "FAILED"));
            
            if (result.passed) {
                passed++;
            } else if (result.error != null) {
                errors++;
            } else {
                failed++;
            }
            
            // Выводим результаты теста
            for (String testResult : test.getTestResults()) {
                logger.info("  - {}", testResult);
            }
            
            if (result.error != null) {
                logger.error("  ERROR: {}", result.error.getMessage());
            }
            
            logger.info("");
        }
        
        logger.info("========================================");
        logger.info("TOTAL: {} tests", results.size());
        logger.info("PASSED: {}", passed);
        logger.info("FAILED: {}", failed);
        logger.info("ERRORS: {}", errors);
        logger.info("========================================");
        
        if (failed > 0 || errors > 0) {
            logger.warn("");
            logger.warn("WARNING: Some tests failed or encountered errors!");
            logger.warn("Review the logs above for details.");
            System.exit(1);
        } else {
            logger.info("");
            logger.info("SUCCESS: All threat tests passed!");
            System.exit(0);
        }
    }
    
    private static class TestResult {
        final ThreatTestBase test;
        final boolean passed;
        final Exception error;
        
        TestResult(ThreatTestBase test, boolean passed, Exception error) {
            this.test = test;
            this.passed = passed;
            this.error = error;
        }
    }
}

