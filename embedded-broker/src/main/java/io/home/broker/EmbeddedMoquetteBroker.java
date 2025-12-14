package io.home.broker;

import io.home.broker.auth.DeviceAuthenticator;
import io.home.broker.interceptor.DeviceInterceptor;
import io.home.registry.DatabaseManager;
import io.home.registry.service.HealthCheckService;
import io.moquette.broker.Server;
import io.moquette.broker.config.IConfig;
import io.moquette.broker.config.MemoryConfig;
import io.moquette.broker.ISslContextCreator;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.ClientAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.Properties;

public class EmbeddedMoquetteBroker implements AutoCloseable {
    
    private static final Logger logger = LoggerFactory.getLogger(EmbeddedMoquetteBroker.class);
    
    private final Server mqttBroker;
    private final int tlsPort;
    private final String serverKeystorePath;
    private final String serverKeystorePassword;
    private final String brokerTruststorePath;
    private final String brokerTruststorePassword;
    private final DatabaseManager dbManager;
    private final DeviceAuthenticator authenticator;
    private final DeviceInterceptor deviceInterceptor;
    private final HealthCheckService healthCheckService;
    private volatile boolean started = false;
    
    public EmbeddedMoquetteBroker(int tlsPort, 
                                   String serverKeystorePath,
                                   String serverKeystorePassword,
                                   String brokerTruststorePath,
                                   String brokerTruststorePassword) {
        this.tlsPort = tlsPort;
        this.serverKeystorePath = serverKeystorePath;
        this.serverKeystorePassword = serverKeystorePassword;
        this.brokerTruststorePath = brokerTruststorePath;
        this.brokerTruststorePassword = brokerTruststorePassword;
        this.mqttBroker = new Server();
        
        // Initialize database and authenticator
        this.dbManager = new DatabaseManager();
        this.authenticator = new DeviceAuthenticator(dbManager);
        this.deviceInterceptor = new DeviceInterceptor(authenticator);
        this.healthCheckService = new HealthCheckService(dbManager);
    }
    
    public EmbeddedMoquetteBroker(String serverKeystorePath,
                                   String serverKeystorePassword,
                                   String brokerTruststorePath,
                                   String brokerTruststorePassword) {
        this(8884, serverKeystorePath, serverKeystorePassword, brokerTruststorePath, brokerTruststorePassword);
    }
    
    public void start() throws Exception {
        if (started) {
            logger.warn("Broker already started");
            return;
        }
        
        logger.info("Starting Moquette MQTT broker on TLS port {}", tlsPort);
        
        Properties props = new Properties();
        props.setProperty(IConfig.PORT_PROPERTY_NAME, "0");
        props.setProperty(IConfig.HOST_PROPERTY_NAME, "0.0.0.0");
        props.setProperty(IConfig.SSL_PORT_PROPERTY_NAME, String.valueOf(tlsPort));
        props.setProperty(IConfig.JKS_PATH_PROPERTY_NAME, serverKeystorePath);
        props.setProperty(IConfig.KEY_STORE_PASSWORD_PROPERTY_NAME, serverKeystorePassword);
        props.setProperty(IConfig.KEY_MANAGER_PASSWORD_PROPERTY_NAME, serverKeystorePassword);
        // Rely on mTLS (ClientAuth.REQUIRE). Allow MQTT anonymous to avoid username requirement.
        props.setProperty(IConfig.ALLOW_ANONYMOUS_PROPERTY_NAME, "true");
        props.setProperty(IConfig.PERSISTENCE_ENABLED_PROPERTY_NAME, "false");
        
        IConfig config = new MemoryConfig(props);
        
        ISslContextCreator sslContextCreator = new ISslContextCreator() {
            @Override
            public SslContext initSSLContext() {
                try {
                    return createSslContext();
                } catch (Exception e) {
                    logger.error("Failed to create SSL context", e);
                    throw new RuntimeException("SSL context creation failed", e);
                }
            }
        };
        
        mqttBroker.startServer(config, null, sslContextCreator, null, new io.home.broker.auth.DeviceAuthorizatorPolicy(authenticator));
        
        // Register device interceptor for authentication and monitoring
        mqttBroker.addInterceptHandler(deviceInterceptor);
        logger.info("Device interceptor registered for authentication and monitoring");
        
        // Start health check monitoring
        healthCheckService.startMonitoring();
        logger.info("Health check monitoring service started");
        
        started = true;
        logger.info("Moquette MQTT broker started successfully on ssl://localhost:{}", tlsPort);
        logger.info("Plaintext port 1883 is DISABLED (set to 0)");
        logger.info("Client authentication is REQUIRED (mTLS)");
        logger.info("Device authentication and monitoring enabled");
        logger.info("Protocols: TLSv1.3, TLSv1.2");
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown hook triggered, stopping broker...");
            try {
                close();
            } catch (Exception e) {
                logger.error("Error stopping broker in shutdown hook", e);
            }
        }));
    }
    
    private SslContext createSslContext() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(serverKeystorePath)) {
            keyStore.load(fis, serverKeystorePassword.toCharArray());
        }
        
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, serverKeystorePassword.toCharArray());
        
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(brokerTruststorePath)) {
            trustStore.load(fis, brokerTruststorePassword.toCharArray());
        }
        
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        
        SslContext sslContext = SslContextBuilder
                .forServer(kmf)
                .trustManager(tmf)
                .clientAuth(ClientAuth.REQUIRE)
                .protocols("TLSv1.3", "TLSv1.2")
                .build();
        
        logger.info("Netty SSL Context created with ClientAuth.REQUIRE");
        logger.info("Enabled protocols: TLSv1.3, TLSv1.2");
        
        return sslContext;
    }
    
    public boolean isStarted() {
        return started;
    }
    
    public DeviceAuthenticator getAuthenticator() {
        return authenticator;
    }
    
    public DatabaseManager getDatabaseManager() {
        return dbManager;
    }
    
    @Override
    public void close() throws Exception {
        if (started) {
            logger.info("Stopping Moquette MQTT broker...");
            
            // Stop health check monitoring
            healthCheckService.stopMonitoring();
            logger.info("Health check monitoring service stopped");
            
            mqttBroker.stopServer();
            started = false;
            logger.info("Moquette MQTT broker stopped");
        }
        
        // Close database connection
        if (dbManager != null) {
            dbManager.close();
        }
    }
}