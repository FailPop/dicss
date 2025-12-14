package io.home.client;

import org.eclipse.paho.client.mqttv3.*;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public class MqttSecureClient implements AutoCloseable {
    
    private static final Logger logger = LoggerFactory.getLogger(MqttSecureClient.class);
    
    private final MqttClient mqttClient;
    private final MqttConnectOptions connOpts;
    private final DeviceInfo deviceInfo;
    private ScheduledExecutorService healthCheckExecutor;
    private String willTopic;
    private String willPayload;
    private int willQos = 1;
    
    private MqttSecureClient(Builder builder) throws Exception {
        String brokerUrl = "ssl://" + builder.host + ":" + builder.port;
        
        this.mqttClient = new MqttClient(brokerUrl, builder.clientId, null);
        this.connOpts = new MqttConnectOptions();
        
        SSLContext sslContext = createSSLContext(
            builder.clientKeystorePath, 
            builder.clientKeystorePassword,
            builder.clientTruststorePath,
            builder.clientTruststorePassword
        );
        
        connOpts.setSocketFactory(sslContext.getSocketFactory());
        connOpts.setCleanSession(builder.cleanSession);
        connOpts.setAutomaticReconnect(builder.autoReconnect);
        connOpts.setConnectionTimeout(builder.connectionTimeout);
        connOpts.setKeepAliveInterval(builder.keepAlive);
        
        // Set Will message if configured
        if (builder.willTopic != null && builder.willPayload != null) {
            this.willTopic = builder.willTopic;
            this.willPayload = builder.willPayload;
            this.willQos = builder.willQos;
            connOpts.setWill(willTopic, willPayload.getBytes(), willQos, false);
            logger.debug("Will message configured: topic={}, qos={}", willTopic, willQos);
        }
        
        this.deviceInfo = builder.deviceInfo;
        
        logger.info("MqttSecureClient initialized for broker: {}, clientId: {}", brokerUrl, builder.clientId);
    }
    
    private SSLContext createSSLContext(String keystorePath, String keystorePassword,
                                        String truststorePath, String truststorePassword) throws Exception {
        // Validate keystore file
        java.io.File keystoreFile = new java.io.File(keystorePath);
        if (!keystoreFile.exists()) {
            String error = "Keystore file does not exist: " + keystorePath;
            logger.error(error);
            throw new java.io.FileNotFoundException(error);
        }
        if (!keystoreFile.canRead()) {
            String error = "Keystore file is not readable: " + keystorePath;
            logger.error(error);
            throw new java.io.IOException(error);
        }
        logger.debug("Keystore file validated: {} (size: {} bytes)", keystorePath, keystoreFile.length());
        
        // Validate truststore file
        java.io.File truststoreFile = new java.io.File(truststorePath);
        if (!truststoreFile.exists()) {
            String error = "Truststore file does not exist: " + truststorePath;
            logger.error(error);
            throw new java.io.FileNotFoundException(error);
        }
        if (!truststoreFile.canRead()) {
            String error = "Truststore file is not readable: " + truststorePath;
            logger.error(error);
            throw new java.io.IOException(error);
        }
        logger.debug("Truststore file validated: {} (size: {} bytes)", truststorePath, truststoreFile.length());
        
        // Load keystore
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(keystorePath)) {
            keyStore.load(fis, keystorePassword.toCharArray());
            logger.debug("Keystore loaded successfully");
        } catch (Exception e) {
            logger.error("Failed to load keystore from {}: {}", keystorePath, e.getMessage());
            logger.debug("Keystore loading error details", e);
            throw new Exception("Failed to load keystore: " + e.getMessage(), e);
        }
        
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        try {
            kmf.init(keyStore, keystorePassword.toCharArray());
            logger.debug("KeyManagerFactory initialized");
        } catch (Exception e) {
            logger.error("Failed to initialize KeyManagerFactory: {}", e.getMessage());
            logger.debug("KeyManagerFactory initialization error details", e);
            throw new Exception("Failed to initialize KeyManagerFactory: " + e.getMessage(), e);
        }
        
        // Load truststore
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(truststorePath)) {
            trustStore.load(fis, truststorePassword.toCharArray());
            logger.debug("Truststore loaded successfully");
        } catch (Exception e) {
            logger.error("Failed to load truststore from {}: {}", truststorePath, e.getMessage());
            logger.debug("Truststore loading error details", e);
            throw new Exception("Failed to load truststore: " + e.getMessage(), e);
        }
        
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        try {
            tmf.init(trustStore);
            logger.debug("TrustManagerFactory initialized");
        } catch (Exception e) {
            logger.error("Failed to initialize TrustManagerFactory: {}", e.getMessage());
            logger.debug("TrustManagerFactory initialization error details", e);
            throw new Exception("Failed to initialize TrustManagerFactory: " + e.getMessage(), e);
        }
        
        // Create SSLContext
        SSLContext sslContext;
        try {
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            logger.info("SSL Context created successfully with TLS protocol");
        } catch (Exception e) {
            logger.error("Failed to create SSLContext: {}", e.getMessage());
            logger.debug("SSLContext creation error details", e);
            throw new Exception("Failed to create SSLContext: " + e.getMessage(), e);
        }
        
        return sslContext;
    }
    
    public void connect() throws MqttException {
        if (!mqttClient.isConnected()) {
            logger.info("Connecting to MQTT broker (clientId: {})...", mqttClient.getClientId());
            try {
                mqttClient.connect(connOpts);
                logger.info("Connected to MQTT broker successfully (clientId: {})", mqttClient.getClientId());
            } catch (MqttException e) {
                logger.error("Failed to connect to MQTT broker (reason code: {}): {}", e.getReasonCode(), e.getMessage());
                if (e.getCause() != null) {
                    logger.error("Connection error cause: {}", e.getCause().getMessage());
                    if (e.getCause() instanceof javax.net.ssl.SSLException) {
                        logger.error("SSL handshake failed. Check certificate configuration:");
                        logger.error("  - Verify keystore and truststore files exist and are readable");
                        logger.error("  - Verify certificates are valid and not expired");
                        logger.error("  - Verify server certificate is in client truststore");
                        logger.error("  - Verify client certificate is in broker truststore");
                    }
                    logger.debug("Full connection error stack trace", e.getCause());
                }
                throw e;
            }
        }
    }
    
    public void publish(String topic, String payload, int qos, boolean retained) throws MqttException {
        if (!mqttClient.isConnected()) {
            throw new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED);
        }
        
        MqttMessage message = new MqttMessage(payload.getBytes());
        message.setQos(qos);
        message.setRetained(retained);
        
        mqttClient.publish(topic, message);
        logger.debug("Published to topic '{}': {}", topic, payload);
    }
    
    public void subscribe(String topic, BiConsumer<String, String> messageListener) throws MqttException {
        if (!mqttClient.isConnected()) {
            throw new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED);
        }
        
        mqttClient.subscribe(topic, (t, msg) -> {
            String payload = new String(msg.getPayload());
            logger.debug("Received message on topic '{}': {}", t, payload);
            messageListener.accept(t, payload);
        });
        
        logger.info("Subscribed to topic: {}", topic);
    }
    
    public boolean isConnected() {
        return mqttClient.isConnected();
    }
    
    public void sendRegistration(DeviceInfo deviceInfo) throws MqttException {
        if (!mqttClient.isConnected()) {
            throw new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED);
        }
        String registrationPayload = String.format(
            "{\"serial\": \"%s\", \"mac\": \"%s\", \"device_type\": \"%s\", \"timestamp\": %d}",
            deviceInfo.getSerial(), deviceInfo.getMac(), deviceInfo.getDeviceType(), System.currentTimeMillis()
        );

        String controllerId = System.getProperty("controller.id", "controller-01");
        String topic = "home/" + controllerId + "/devices/" + deviceInfo.getSerial() + "/register";

        MqttMessage message = new MqttMessage(registrationPayload.getBytes());
        message.setQos(1); // Registration: At Least Once
        message.setRetained(false);

        mqttClient.publish(topic, message);
        logger.info("Registration sent for device: {}", deviceInfo.getSerial());
    }
    
    public void startHealthCheck(DeviceInfo deviceInfo, long intervalMs) {
        if (healthCheckExecutor != null && !healthCheckExecutor.isShutdown()) {
            logger.warn("Health check already running");
            return;
        }
        
        healthCheckExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "health-check-" + deviceInfo.getSerial());
            t.setDaemon(true);
            return t;
        });
        
        healthCheckExecutor.scheduleAtFixedRate(() -> {
            try {
                sendHealthCheck(deviceInfo);
            } catch (Exception e) {
                logger.error("Error sending health check", e);
            }
        }, 0, intervalMs, TimeUnit.MILLISECONDS);
        
        logger.info("Health check started for device: {} with interval: {}ms", deviceInfo.getSerial(), intervalMs);
    }
    
    private void sendHealthCheck(DeviceInfo deviceInfo) throws MqttException {
        if (!mqttClient.isConnected()) {
            logger.warn("MQTT client not connected, skipping health check");
            return;
        }
        
        JSONObject healthData = new JSONObject();
        healthData.put("serial", deviceInfo.getSerial());
        healthData.put("mac", deviceInfo.getMac());
        healthData.put("timestamp", java.time.LocalDateTime.now().toString());
        healthData.put("battery_level", getBatteryLevel());
        healthData.put("uptime", getUptime());
        healthData.put("temperature", 25.5);
        healthData.put("signal_strength", -45);
        
        String healthPayload = healthData.toString();
        
        String controllerId = System.getProperty("controller.id", "controller-01");
        String topic = "home/" + controllerId + "/devices/" + deviceInfo.getSerial() + "/health";
        MqttMessage message = new MqttMessage(healthPayload.getBytes());
        message.setQos(1); // Health check: At Least Once
        message.setRetained(false);

        mqttClient.publish(topic, message);
        logger.debug("Health check sent for device: {}", deviceInfo.getSerial());
    }
    
    private int getBatteryLevel() {
        // Simulate battery level (in real implementation would read from device)
        return 85 + (int)(Math.random() * 15); // 85-100%
    }
    
    private long getUptime() {
        // Simulate uptime in seconds (in real implementation would read from device)
        return System.currentTimeMillis() / 1000;
    }
    
    public DeviceInfo getDeviceInfo() {
        return deviceInfo;
    }
    
    @Override
    public void close() throws Exception {
        if (healthCheckExecutor != null && !healthCheckExecutor.isShutdown()) {
            logger.info("Stopping health check executor...");
            healthCheckExecutor.shutdown();
            try {
                if (!healthCheckExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    healthCheckExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                healthCheckExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        if (mqttClient.isConnected()) {
            logger.info("Disconnecting MQTT client...");
            mqttClient.disconnect();
            logger.info("MQTT client disconnected");
        }
        mqttClient.close();
    }
    
    public static class Builder {
        private String host = "localhost";
        private int port = 8884;
        private String clientId;
        private DeviceInfo deviceInfo;
        private String clientIdSuffix;
        private String clientKeystorePath;
        private String clientKeystorePassword;
        private String clientTruststorePath;
        private String clientTruststorePassword;
        private boolean cleanSession = true;
        private boolean autoReconnect = true; // Changed default to true for resilience
        private int connectionTimeout = 30;
        private int keepAlive = 60;
        private String willTopic;
        private String willPayload;
        private int willQos = 1;
        
        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }
        
        public Builder deviceInfo(DeviceInfo deviceInfo) {
            this.deviceInfo = deviceInfo;
            return this;
        }
        
        public Builder deviceInfo(DeviceInfo deviceInfo, String clientIdSuffix) {
            this.deviceInfo = deviceInfo;
            this.clientIdSuffix = clientIdSuffix;
            return this;
        }
        
        public Builder host(String host) {
            this.host = host;
            return this;
        }
        
        public Builder port(int port) {
            this.port = port;
            return this;
        }
        
        public Builder clientKeystore(String path, String password) {
            this.clientKeystorePath = path;
            this.clientKeystorePassword = password;
            return this;
        }
        
        public Builder clientTruststore(String path, String password) {
            this.clientTruststorePath = path;
            this.clientTruststorePassword = password;
            return this;
        }
        
        public Builder cleanSession(boolean cleanSession) {
            this.cleanSession = cleanSession;
            return this;
        }
        
        public Builder autoReconnect(boolean autoReconnect) {
            this.autoReconnect = autoReconnect;
            return this;
        }
        
        public Builder connectionTimeout(int seconds) {
            this.connectionTimeout = seconds;
            return this;
        }
        
        public Builder keepAlive(int seconds) {
            this.keepAlive = seconds;
            return this;
        }
        
        public Builder willMessage(String topic, String payload, int qos) {
            this.willTopic = topic;
            this.willPayload = payload;
            this.willQos = qos;
            return this;
        }
        
        public MqttSecureClient build() throws Exception {
            if (deviceInfo != null) {
                // Auto-generate clientId from device info
                if (clientIdSuffix != null && !clientIdSuffix.isEmpty()) {
                    this.clientId = deviceInfo.generateClientId(clientIdSuffix);
                } else {
                    this.clientId = deviceInfo.generateClientId();
                }
            } else if (clientId == null || clientId.isEmpty()) {
                throw new IllegalArgumentException("Either clientId or deviceInfo is required");
            }
            
            if (clientKeystorePath == null || clientTruststorePath == null) {
                throw new IllegalArgumentException("Both keystore and truststore paths are required");
            }
            return new MqttSecureClient(this);
        }
    }
}