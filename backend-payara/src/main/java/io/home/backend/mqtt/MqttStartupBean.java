package io.home.backend.mqtt;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class MqttStartupBean {
    
    private static final Logger logger = LoggerFactory.getLogger(MqttStartupBean.class);
    
    @Inject
    private MqttClientManager mqttClientManager;
    
    public void onStartup(@Observes @Initialized(ApplicationScoped.class) Object init) {
        logger.info("Application context initialized, manually initializing MQTT clients");
        mqttClientManager.init();
    }
}

