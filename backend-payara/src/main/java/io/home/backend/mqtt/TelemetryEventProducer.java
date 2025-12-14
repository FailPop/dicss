package io.home.backend.mqtt;

import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import jakarta.jms.Queue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class TelemetryEventProducer {

    private static final Logger logger = LoggerFactory.getLogger(TelemetryEventProducer.class);

    @Resource(lookup = "jms/ConnectionFactory")
    private ConnectionFactory connectionFactory;

    @Resource(lookup = "jms/telemetryQueue")
    private Queue telemetryQueue;

    public void send(String topic, String payload) throws JMSException {
        try (JMSContext context = connectionFactory.createContext()) {
            String body = String.format("{\"topic\":\"%s\",\"payload\":%s}", topic, toJsonValue(payload));
            context.createProducer().send(telemetryQueue, body);
        } catch (Exception e) {
            logger.error("Error producing telemetry JMS message", e);
            if (e instanceof JMSException) throw (JMSException)e;
            throw new JMSException(e.getMessage());
        }
    }

    private String toJsonValue(String s) {
        if (s == null) return "null";
        s = s.trim();
        if (s.startsWith("{") || s.startsWith("[")) {
            return s; // already JSON
        }
        return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}


