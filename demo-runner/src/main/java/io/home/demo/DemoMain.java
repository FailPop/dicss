package io.home.demo;

import io.home.controller.core.BrokerService;
import io.home.controller.web.ControllerWebServer;
import io.home.controller.core.CertRotationService;
import io.home.client.DeviceInfo;
import io.home.demo.SimulatedDevice;
import io.home.demo.DeviceInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class DemoMain {
    
    private static final Logger logger = LoggerFactory.getLogger(DemoMain.class);
    
    public static void main(String[] args) {
        String serverKeystorePath = System.getProperty("server.keystore", "server-keystore.p12");
        String serverKeystorePassword = System.getProperty("server.keystore.password", "changeit");
        String brokerTruststorePath = System.getProperty("broker.truststore", "broker-truststore.p12");
        String brokerTruststorePassword = System.getProperty("broker.truststore.password", "changeit");
        
        String clientKeystorePath = System.getProperty("client.keystore", "client.p12");
        String clientKeystorePassword = System.getProperty("client.keystore.password", "changeit");
        String clientTruststorePath = System.getProperty("client.truststore", "client-truststore.p12");
        String clientTruststorePassword = System.getProperty("client.truststore.password", "changeit");
        
        String mode = System.getProperty("mode", "demo");
        boolean startBroker = !Boolean.parseBoolean(System.getProperty("skip.broker", "false"));
        boolean testCloning = Boolean.parseBoolean(System.getProperty("test.cloning", "true"));
        boolean brokerOnlyMode = "broker-only".equals(mode);
        
        logger.info("=== IoT Device Security Demo ===");
        logger.info("Mode: {}", mode);
        logger.info("Server Keystore: {}", serverKeystorePath);
        logger.info("Broker Truststore: {}", brokerTruststorePath);
        logger.info("Client Keystore: {}", clientKeystorePath);
        logger.info("Client Truststore: {}", clientTruststorePath);
        logger.info("Start embedded broker: {}", startBroker);
        logger.info("Test device cloning: {}", testCloning);
        logger.info("================================");
        
        // Initialize demo devices only if not in broker-only mode
        DeviceInitializer deviceInitializer = null;
        if (!brokerOnlyMode) {
            deviceInitializer = new DeviceInitializer();
            boolean clearExisting = Boolean.parseBoolean(System.getProperty("init.clear", "true"));
            deviceInitializer.initializeDatabase(clearExisting);
        }
        
        BrokerService brokerService = BrokerService.getInstance();
        ControllerWebServer webServer = null;
        CertRotationService certRotationService = null;
        List<SimulatedDevice> devices = new ArrayList<>();
        
        try {
            if (startBroker) {
                brokerService.start(
                    serverKeystorePath,
                    serverKeystorePassword,
                    brokerTruststorePath,
                    brokerTruststorePassword
                );
                Thread.sleep(3000); // Wait for broker to start
            }
            
            if (brokerOnlyMode) {
                // Broker-only mode: just keep the broker running
                logger.info("Broker-only mode: Broker is running. Press Ctrl+C to stop.");
                // Wait indefinitely until interrupted
                while (true) {
                    Thread.sleep(60000); // Sleep 1 minute at a time
                }
            }
            
            // Create test devices
            List<DeviceInfo> deviceInfos = createTestDevices();
            
            // Start devices
            logger.info("Starting {} simulated devices...", deviceInfos.size());
            for (DeviceInfo deviceInfo : deviceInfos) {
                SimulatedDevice device = new SimulatedDevice(
                    deviceInfo,
                    clientKeystorePath, clientKeystorePassword,
                    clientTruststorePath, clientTruststorePassword
                );
                device.start();
                devices.add(device);
                
                Thread.sleep(1000); // Stagger device starts
            }
            
            logger.info("All devices started successfully");
            
            // Wait for devices to register and send telemetry
            Thread.sleep(10000);
            
            // Test device cloning if enabled
            if (testCloning) {
                testDeviceCloning(deviceInfos.get(0), clientKeystorePath, clientKeystorePassword, 
                                clientTruststorePath, clientTruststorePassword);
            }
            
            // Let devices run for a while
            // Start controller web server (local-only by default)
            try {
                ControllerWebServer.Config cfg = new ControllerWebServer.Config();
                webServer = new ControllerWebServer(cfg);
                webServer.start();
            } catch (Exception e) {
                logger.error("Failed to start ControllerWebServer", e);
            }

            // Start certificate rotation service (randomized schedule + file-change detect)
            try {
                certRotationService = new CertRotationService(
                    serverKeystorePath,
                    brokerTruststorePath,
                    clientKeystorePath,
                    clientTruststorePath
                );
                certRotationService.start();
            } catch (Exception e) {
                logger.error("Failed to start CertRotationService", e);
            }

            logger.info("Devices running... Press Ctrl+C to stop");
            Thread.sleep(30000);
            
            logger.info("Demo completed successfully");
            
        } catch (Exception e) {
            logger.error("Demo failed with error", e);
            System.exit(1);
        } finally {
            // Stop all devices
            for (SimulatedDevice device : devices) {
                device.stop();
            }
            
            if (webServer != null) {
                try { webServer.close(); } catch (Exception ignored) {}
            }
            if (certRotationService != null) {
                try { certRotationService.close(); } catch (Exception ignored) {}
            }
            if (brokerService != null && brokerService.isStarted()) {
                brokerService.stop();
            }
            
            if (deviceInitializer != null) {
                deviceInitializer.close();
            }
            
            logger.info("Demo cleanup completed");
        }
        
        // Add shutdown hook for graceful termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown hook triggered, performing cleanup...");
            try {
                // Cleanup already handled in finally block
                logger.info("Shutdown cleanup completed");
            } catch (Exception e) {
                logger.error("Error during shutdown cleanup", e);
            }
        }));
    }
    
    private static List<DeviceInfo> createTestDevices() {
        List<DeviceInfo> devices = new ArrayList<>();
        
        // Generate random MAC addresses for security (not sequential)
        java.util.Random random = new java.util.Random();
        devices.add(new DeviceInfo("IOT-2025-0001", generateRandomMac(random), "TEMP_SENSOR"));
        devices.add(new DeviceInfo("IOT-2025-0002", generateRandomMac(random), "SMART_PLUG"));
        devices.add(new DeviceInfo("IOT-2025-0003", generateRandomMac(random), "ENERGY_SENSOR"));
        devices.add(new DeviceInfo("IOT-2025-0004", generateRandomMac(random), "SMART_SWITCH"));
        
        return devices;
    }
    
    private static String generateRandomMac(java.util.Random random) {
        // Generate random MAC address: XX:XX:XX:XX:XX:XX
        StringBuilder mac = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            if (i > 0) mac.append(":");
            int octet = random.nextInt(256);
            mac.append(String.format("%02X", octet));
        }
        return mac.toString();
    }
    
    private static void testDeviceCloning(DeviceInfo originalDevice, 
                                         String clientKeystorePath, String clientKeystorePassword,
                                         String clientTruststorePath, String clientTruststorePassword) {
        logger.info("=== Testing Device Cloning Detection ===");
        logger.info("Attempting to clone device: {}", originalDevice.getSerial());
        
        try {
            // Create a clone device with same serial and MAC but different IP (simulated)
            SimulatedDevice cloneDevice = new SimulatedDevice(
                originalDevice,
                clientKeystorePath, clientKeystorePassword,
                clientTruststorePath, clientTruststorePassword,
                true // isClone = true
            );
            
            logger.warn("Starting clone device - this should trigger security alert!");
            cloneDevice.start();
            
            // Let it run for a few seconds to trigger the security system
            Thread.sleep(5000);
            
            cloneDevice.stop();
            logger.info("Clone device stopped");
            
        } catch (Exception e) {
            logger.error("Error during cloning test", e);
        }
        
        logger.info("=== Cloning Test Completed ===");
    }
}