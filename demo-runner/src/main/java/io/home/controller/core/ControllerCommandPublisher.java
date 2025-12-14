package io.home.controller.core;

import io.home.client.MqttSecureClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ControllerCommandPublisher implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(ControllerCommandPublisher.class);

    private final String controllerId;
    private final MqttSecureClient mqttClient;

    public ControllerCommandPublisher(String host, int port,
                                      String controllerId,
                                      String clientKeystorePath, String clientKeystorePassword,
                                      String clientTruststorePath, String clientTruststorePassword) throws Exception {
        this.controllerId = controllerId;
        this.mqttClient = new MqttSecureClient.Builder()
            .host(host)
            .port(port)
            .clientId("controller-cmd")
            .clientKeystore(clientKeystorePath, clientKeystorePassword)
            .clientTruststore(clientTruststorePath, clientTruststorePassword)
            .cleanSession(true)
            .autoReconnect(true)
            .build();
        this.mqttClient.connect();
    }

    public void sendCommand(String deviceId, String payload) throws MqttException {
        // Commands use QoS=2 (Exactly Once) for guaranteed delivery
        String topic = String.format("home/%s/devices/%s/cmd", controllerId, deviceId);
        mqttClient.publish(topic, payload, 2, false);
        logger.info("Command published: device={}, topic={}, qos=2", deviceId, topic);
    }

    @Override
    public void close() throws Exception {
        mqttClient.close();
    }
}


