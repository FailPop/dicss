package io.home.backend.mqtt;

import io.home.backend.config.MqttConfig;
import io.home.client.MqttSecureClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;

@ApplicationScoped
public class MqttClientManager {
    
    private static final Logger logger = LoggerFactory.getLogger(MqttClientManager.class);
    private static final String TELEMETRY_TOPIC = "home/+/devices/+/telemetry";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 2000;
    
    @Inject
    private MqttConfig config;
    
    private MqttSecureClient publisherClient;
    private MqttSecureClient subscriberClient;
    private boolean publisherConnected = false;
    private boolean subscriberConnected = false;
    
    @Inject
    private io.home.backend.mqtt.TelemetryEventProducer telemetryEventProducer;
    
    @PostConstruct
    public void init() {
        logger.info("Initializing MQTT clients for Payara backend...");
        logger.info("MQTT configuration: host={}, port={}", config.getHost(), config.getPort());
        
        try {
            logger.info("Validating certificate paths...");
            String keystorePath = config.getClientKeystorePath();
            String truststorePath = config.getClientTruststorePath();
            logger.info("Keystore path: {}", keystorePath);
            logger.info("Truststore path: {}", truststorePath);
        } catch (Exception e) {
            logger.error("Certificate validation failed - MQTT clients will not be initialized", e);
            return;
        }
        
        publisherConnected = initializePublisher();
        subscriberConnected = initializeSubscriber();
        
        if (!publisherConnected && !subscriberConnected) {
            logger.error("Failed to initialize both MQTT clients. MQTT functionality will be unavailable.");
        } else if (!publisherConnected) {
            logger.warn("Publisher client failed to connect, but subscriber is connected.");
        } else if (!subscriberConnected) {
            logger.warn("Subscriber client failed to connect, but publisher is connected.");
        } else {
            logger.info("Both MQTT clients successfully initialized and connected.");
        }
    }
    
    private boolean initializePublisher() {
        logger.info("Initializing MQTT Publisher client...");
        
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                if (publisherClient != null) {
                    try {
                        publisherClient.close();
                    } catch (Exception e) {
                        logger.debug("Error closing previous publisher client", e);
                    }
                }
                
                publisherClient = new MqttSecureClient.Builder()
                        .clientId("payara-publisher-" + System.currentTimeMillis())
                        .host(config.getHost())
                        .port(config.getPort())
                        .clientKeystore(config.getClientKeystorePath(), config.getClientKeystorePassword())
                        .clientTruststore(config.getClientTruststorePath(), config.getClientTruststorePassword())
                        .cleanSession(true)
                        .autoReconnect(true)
                        .connectionTimeout(30)
                        .build();
                
                publisherClient.connect();
                logger.info("MQTT Publisher client connected successfully (attempt {}/{})", attempt, MAX_RETRY_ATTEMPTS);
                return true;
                
            } catch (SSLException e) {
                logger.error("SSL error connecting publisher client (attempt {}/{}): {}", attempt, MAX_RETRY_ATTEMPTS, e.getMessage());
                logger.debug("SSL error details", e);
                if (e.getCause() != null) {
                    logger.error("SSL error cause: {}", e.getCause().getMessage());
                }
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    logger.info("Retrying publisher connection in {} ms...", RETRY_DELAY_MS);
                    sleep(RETRY_DELAY_MS);
                }
            } catch (MqttException e) {
                logger.error("MQTT error connecting publisher client (attempt {}/{}): {}", attempt, MAX_RETRY_ATTEMPTS, e.getMessage());
                logger.debug("MQTT error details", e);
                if (e.getCause() != null) {
                    logger.error("MQTT error cause: {}", e.getCause().getMessage());
                }
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    logger.info("Retrying publisher connection in {} ms...", RETRY_DELAY_MS);
                    sleep(RETRY_DELAY_MS);
                }
            } catch (Exception e) {
                logger.error("Unexpected error connecting publisher client (attempt {}/{}): {}", attempt, MAX_RETRY_ATTEMPTS, e.getMessage(), e);
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    logger.info("Retrying publisher connection in {} ms...", RETRY_DELAY_MS);
                    sleep(RETRY_DELAY_MS);
                }
            }
        }
        
        logger.error("Failed to connect publisher client after {} attempts", MAX_RETRY_ATTEMPTS);
        return false;
    }
    
    private boolean initializeSubscriber() {
        logger.info("Initializing MQTT Subscriber client...");
        
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                if (subscriberClient != null) {
                    try {
                        subscriberClient.close();
                    } catch (Exception e) {
                        logger.debug("Error closing previous subscriber client", e);
                    }
                }
                
                subscriberClient = new MqttSecureClient.Builder()
                        .clientId("payara-subscriber-" + System.currentTimeMillis())
                        .host(config.getHost())
                        .port(config.getPort())
                        .clientKeystore(config.getClientKeystorePath(), config.getClientKeystorePassword())
                        .clientTruststore(config.getClientTruststorePath(), config.getClientTruststorePassword())
                        .cleanSession(true)
                        .autoReconnect(true)
                        .connectionTimeout(30)
                        .build();
                
                subscriberClient.connect();
                
                subscriberClient.subscribe(TELEMETRY_TOPIC, (topic, payload) -> {
                    logger.debug("[MQTT SUBSCRIBER] Received telemetry on topic '{}': {}", topic, payload);
                    try {
                        telemetryEventProducer.send(topic, payload);
                    } catch (Exception e) {
                        logger.error("Failed to send telemetry to JMS", e);
                    }
                });
                
                logger.info("MQTT Subscriber client connected and subscribed to '{}' (attempt {}/{})", TELEMETRY_TOPIC, attempt, MAX_RETRY_ATTEMPTS);
                return true;
                
            } catch (SSLException e) {
                logger.error("SSL error connecting subscriber client (attempt {}/{}): {}", attempt, MAX_RETRY_ATTEMPTS, e.getMessage());
                logger.debug("SSL error details", e);
                if (e.getCause() != null) {
                    logger.error("SSL error cause: {}", e.getCause().getMessage());
                }
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    logger.info("Retrying subscriber connection in {} ms...", RETRY_DELAY_MS);
                    sleep(RETRY_DELAY_MS);
                }
            } catch (MqttException e) {
                logger.error("MQTT error connecting subscriber client (attempt {}/{}): {}", attempt, MAX_RETRY_ATTEMPTS, e.getMessage());
                logger.debug("MQTT error details", e);
                if (e.getCause() != null) {
                    logger.error("MQTT error cause: {}", e.getCause().getMessage());
                }
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    logger.info("Retrying subscriber connection in {} ms...", RETRY_DELAY_MS);
                    sleep(RETRY_DELAY_MS);
                }
            } catch (Exception e) {
                logger.error("Unexpected error connecting subscriber client (attempt {}/{}): {}", attempt, MAX_RETRY_ATTEMPTS, e.getMessage(), e);
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    logger.info("Retrying subscriber connection in {} ms...", RETRY_DELAY_MS);
                    sleep(RETRY_DELAY_MS);
                }
            }
        }
        
        logger.error("Failed to connect subscriber client after {} attempts", MAX_RETRY_ATTEMPTS);
        return false;
    }
    
    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Sleep interrupted", e);
        }
    }
    
    public void publish(String topic, String payload) throws Exception {
        if (!publisherConnected || publisherClient == null || !publisherClient.isConnected()) {
            throw new IllegalStateException("MQTT publisher client is not connected. Publisher initialized: " + publisherConnected);
        }
        publisherClient.publish(topic, payload, 1, false);
        logger.debug("Published to MQTT topic '{}': {}", topic, payload);
    }
    
    public boolean isPublisherConnected() {
        return publisherConnected && publisherClient != null && publisherClient.isConnected();
    }
    
    public boolean isSubscriberConnected() {
        return subscriberConnected && subscriberClient != null && subscriberClient.isConnected();
    }
    
    @PreDestroy
    public void destroy() {
        logger.info("Shutting down MQTT clients...");
        
        if (publisherClient != null) {
            try {
                publisherClient.close();
                logger.info("Publisher client closed");
            } catch (Exception e) {
                logger.error("Error closing publisher client", e);
            }
        }
        
        if (subscriberClient != null) {
            try {
                subscriberClient.close();
                logger.info("Subscriber client closed");
            } catch (Exception e) {
                logger.error("Error closing subscriber client", e);
            }
        }
    }
}

