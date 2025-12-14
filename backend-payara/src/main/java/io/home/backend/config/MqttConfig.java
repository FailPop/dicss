package io.home.backend.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Optional;

@ApplicationScoped
public class MqttConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(MqttConfig.class);
    
    @Inject
    @ConfigProperty(name = "mqtt.host", defaultValue = "localhost")
    private Optional<String> configHost;
    
    @Inject
    @ConfigProperty(name = "mqtt.port", defaultValue = "8884")
    private Optional<String> configPort;
    
    @Inject
    @ConfigProperty(name = "mqtt.client.keystore.path", defaultValue = "")
    private Optional<String> configKeystorePath;
    
    @Inject
    @ConfigProperty(name = "mqtt.client.keystore.password", defaultValue = "changeit")
    private Optional<String> configKeystorePassword;
    
    @Inject
    @ConfigProperty(name = "mqtt.client.truststore.path", defaultValue = "")
    private Optional<String> configTruststorePath;
    
    @Inject
    @ConfigProperty(name = "mqtt.client.truststore.password", defaultValue = "changeit")
    private Optional<String> configTruststorePassword;
    
    public String getHost() {
        String host = configHost.orElse(null);
        if (host == null || host.trim().isEmpty()) {
            host = System.getProperty("mqtt.host", "localhost");
        }
        logger.debug("MQTT host: {}", host);
        return host;
    }
    
    public int getPort() {
        String portStr = configPort.orElse(null);
        if (portStr == null || portStr.trim().isEmpty()) {
            portStr = System.getProperty("mqtt.port", "8884");
        }
        try {
            int port = Integer.parseInt(portStr);
            logger.debug("MQTT port: {}", port);
            return port;
        } catch (NumberFormatException e) {
            logger.warn("Invalid MQTT port value '{}', using default 8884", portStr);
            return 8884;
        }
    }
    
    public String getClientKeystorePath() {
        String path = configKeystorePath.orElse(null);
        if (path == null || path.trim().isEmpty()) {
            path = System.getProperty("mqtt.client.keystore.path");
        }
        if (path == null || path.trim().isEmpty()) {
            logger.error("mqtt.client.keystore.path is not configured in system properties or microprofile-config.properties");
            throw new IllegalStateException("mqtt.client.keystore.path is not configured");
        }
        
        File keystoreFile = new File(path);
        if (!keystoreFile.exists()) {
            logger.error("MQTT client keystore file does not exist: {}", path);
            throw new IllegalStateException("MQTT client keystore file does not exist: " + path);
        }
        if (!keystoreFile.canRead()) {
            logger.error("MQTT client keystore file is not readable: {}", path);
            throw new IllegalStateException("MQTT client keystore file is not readable: " + path);
        }
        
        logger.debug("MQTT client keystore path: {}", path);
        return path;
    }
    
    public String getClientKeystorePassword() {
        String password = configKeystorePassword.orElse(null);
        if (password == null || password.trim().isEmpty()) {
            password = System.getProperty("mqtt.client.keystore.password", "changeit");
        }
        logger.debug("MQTT client keystore password configured (length: {})", password != null ? password.length() : 0);
        return password;
    }
    
    public String getClientTruststorePath() {
        String path = configTruststorePath.orElse(null);
        if (path == null || path.trim().isEmpty()) {
            path = System.getProperty("mqtt.client.truststore.path");
        }
        if (path == null || path.trim().isEmpty()) {
            logger.error("mqtt.client.truststore.path is not configured in system properties or microprofile-config.properties");
            throw new IllegalStateException("mqtt.client.truststore.path is not configured");
        }
        
        File truststoreFile = new File(path);
        if (!truststoreFile.exists()) {
            logger.error("MQTT client truststore file does not exist: {}", path);
            throw new IllegalStateException("MQTT client truststore file does not exist: " + path);
        }
        if (!truststoreFile.canRead()) {
            logger.error("MQTT client truststore file is not readable: {}", path);
            throw new IllegalStateException("MQTT client truststore file is not readable: " + path);
        }
        
        logger.debug("MQTT client truststore path: {}", path);
        return path;
    }
    
    public String getClientTruststorePassword() {
        String password = configTruststorePassword.orElse(null);
        if (password == null || password.trim().isEmpty()) {
            password = System.getProperty("mqtt.client.truststore.password", "changeit");
        }
        logger.debug("MQTT client truststore password configured (length: {})", password != null ? password.length() : 0);
        return password;
    }
}

