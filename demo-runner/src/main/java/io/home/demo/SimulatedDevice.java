package io.home.demo;

import io.home.client.DeviceInfo;
import io.home.client.MqttSecureClient;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SimulatedDevice {
    
    private static final Logger logger = LoggerFactory.getLogger(SimulatedDevice.class);
    
    private final DeviceInfo deviceInfo;
    private final String clientKeystorePath;
    private final String clientKeystorePassword;
    private final String clientTruststorePath;
    private final String clientTruststorePassword;
    
    private MqttSecureClient mqttClient;
    private CountDownLatch registrationLatch;
    private boolean isClone = false;
    
    public SimulatedDevice(DeviceInfo deviceInfo, 
                          String clientKeystorePath, String clientKeystorePassword,
                          String clientTruststorePath, String clientTruststorePassword) {
        this.deviceInfo = deviceInfo;
        this.clientKeystorePath = clientKeystorePath;
        this.clientKeystorePassword = clientKeystorePassword;
        this.clientTruststorePath = clientTruststorePath;
        this.clientTruststorePassword = clientTruststorePassword;
    }
    
    public SimulatedDevice(DeviceInfo deviceInfo, 
                          String clientKeystorePath, String clientKeystorePassword,
                          String clientTruststorePath, String clientTruststorePassword,
                          boolean isClone) {
        this.deviceInfo = deviceInfo;
        this.clientKeystorePath = clientKeystorePath;
        this.clientKeystorePassword = clientKeystorePassword;
        this.clientTruststorePath = clientTruststorePath;
        this.clientTruststorePassword = clientTruststorePassword;
        this.isClone = isClone;
    }
    
    public void start() throws Exception {
        logger.info("Starting simulated device: {}", deviceInfo);
        
        // Create MQTT client with device info
        String controllerId = System.getProperty("controller.id", "controller-01");
        String willTopic = "home/" + controllerId + "/devices/" + deviceInfo.getSerial() + "/offline";
        String willPayload = String.format("{\"serial\": \"%s\", \"reason\": \"connection_lost\"}", deviceInfo.getSerial());
        
        MqttSecureClient.Builder builder = new MqttSecureClient.Builder()
                .host("localhost")
                .port(8884)
                .clientKeystore(clientKeystorePath, clientKeystorePassword)
                .clientTruststore(clientTruststorePath, clientTruststorePassword)
                .cleanSession(true)
                .autoReconnect(true)
                .willMessage(willTopic, willPayload, 1); // Will message for offline notification
        
        if (isClone) {
            builder.deviceInfo(deviceInfo, "-CLONE");
        } else {
            builder.deviceInfo(deviceInfo);
        }
        
        mqttClient = builder.build();
        
        // Connect to broker
        mqttClient.connect();
        logger.info("Device {} connected to MQTT broker", deviceInfo.getSerial());
        
        // Send registration
        mqttClient.sendRegistration(deviceInfo);
        logger.info("Device {} registration sent", deviceInfo.getSerial());
        
        // Start health check (every 60 seconds)
        mqttClient.startHealthCheck(deviceInfo, 60000);
        
        // Subscribe to device-specific topics
        subscribeToDeviceTopics();
        
        // Start telemetry simulation
        startTelemetrySimulation();
    }
    
    private void subscribeToDeviceTopics() throws Exception {
        String controllerId = System.getProperty("controller.id", "controller-01");
        String commandTopic = "home/" + controllerId + "/devices/" + deviceInfo.getSerial() + "/cmd";
        
        mqttClient.subscribe(commandTopic, (topic, payload) -> {
            logger.info("[DEVICE {}] Received command: {}", deviceInfo.getSerial(), payload);
            // Process command and send status update
            sendStatusUpdate("Command processed: " + payload);
        });
        
        logger.info("Device {} subscribed to command topic: {}", deviceInfo.getSerial(), commandTopic);
    }
    
    private void startTelemetrySimulation() {
        Thread telemetryThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    sendTelemetry();
                    Thread.sleep(5000); // Send telemetry every 5 seconds
                }
            } catch (InterruptedException e) {
                logger.info("Telemetry simulation stopped for device: {}", deviceInfo.getSerial());
                Thread.currentThread().interrupt();
            }
        }, "telemetry-" + deviceInfo.getSerial());
        
        telemetryThread.setDaemon(true);
        telemetryThread.start();
        
        logger.info("Telemetry simulation started for device: {}", deviceInfo.getSerial());
    }
    
    private void sendTelemetry() {
        try {
        String controllerId = System.getProperty("controller.id", "controller-01");
        String telemetryTopic = "home/" + controllerId + "/devices/" + deviceInfo.getSerial() + "/telemetry";
            String telemetryPayload = generateTelemetryPayload();
            
            // Telemetry QoS: 0 for sensors, 1 for actuators (SMART_PLUG, SMART_SWITCH)
            int qos = ("SMART_PLUG".equals(deviceInfo.getDeviceType()) || 
                      "SMART_SWITCH".equals(deviceInfo.getDeviceType())) ? 1 : 0;
            mqttClient.publish(telemetryTopic, telemetryPayload, qos, false);
            logger.debug("[DEVICE {}] Telemetry sent: {}", deviceInfo.getSerial(), telemetryPayload);
            
        } catch (Exception e) {
            logger.error("Error sending telemetry for device: {}", deviceInfo.getSerial(), e);
        }
    }
    
    private void sendStatusUpdate(String status) {
        try {
            String statusTopic = "device/" + deviceInfo.getDeviceType() + "/" + deviceInfo.getSerial() + "/status";
            String statusPayload = String.format(
                "{\"serial\": \"%s\", \"status\": \"%s\", \"timestamp\": %d}",
                deviceInfo.getSerial(), status, System.currentTimeMillis()
            );
            
            mqttClient.publish(statusTopic, statusPayload, 1, false);
            logger.info("[DEVICE {}] Status update sent: {}", deviceInfo.getSerial(), status);
            
        } catch (Exception e) {
            logger.error("Error sending status update for device: {}", deviceInfo.getSerial(), e);
        }
    }
    
    private String generateTelemetryPayload() {
        long timestamp = System.currentTimeMillis();
        
        switch (deviceInfo.getDeviceType()) {
            case "TEMP_SENSOR":
                double temperature = 20.0 + (Math.random() * 10.0); // 20-30°C
                return String.format(
                    "{\"serial\": \"%s\", \"temperature\": %.2f, \"humidity\": %.1f, \"timestamp\": %d}",
                    deviceInfo.getSerial(), temperature, 45.0 + Math.random() * 20.0, timestamp
                );
                
            case "SMART_PLUG":
                double power = Math.random() * 100.0; // 0-100W
                boolean isOn = Math.random() > 0.5;
                return String.format(
                    "{\"serial\": \"%s\", \"power\": %.2f, \"is_on\": %s, \"timestamp\": %d}",
                    deviceInfo.getSerial(), power, isOn, timestamp
                );
                
            case "ENERGY_SENSOR":
                double voltage = 220.0 + (Math.random() * 20.0 - 10.0); // 210-230V
                double current = Math.random() * 5.0; // 0-5A
                return String.format(
                    "{\"serial\": \"%s\", \"voltage\": %.2f, \"current\": %.2f, \"timestamp\": %d}",
                    deviceInfo.getSerial(), voltage, current, timestamp
                );
                
            case "SMART_SWITCH":
                boolean switchState = Math.random() > 0.5;
                return String.format(
                    "{\"serial\": \"%s\", \"state\": %s, \"timestamp\": %d}",
                    deviceInfo.getSerial(), switchState, timestamp
                );
                
            default:
                return String.format(
                    "{\"serial\": \"%s\", \"data\": \"unknown\", \"timestamp\": %d}",
                    deviceInfo.getSerial(), timestamp
                );
        }
    }
    
    public void stop() {
        try {
            if (mqttClient != null) {
                mqttClient.close();
                logger.info("Device {} stopped", deviceInfo.getSerial());
            }
        } catch (Exception e) {
            logger.error("Error stopping device: {}", deviceInfo.getSerial(), e);
        }
    }
    
    public DeviceInfo getDeviceInfo() {
        return deviceInfo;
    }
    
    public boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }
}
