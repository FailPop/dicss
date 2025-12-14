package io.home.backend.rest;

import io.home.backend.mqtt.MqttClientManager;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/telemetry")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
public class TelemetryResource {
    
    private static final Logger logger = LoggerFactory.getLogger(TelemetryResource.class);
    private static final String DEFAULT_TOPIC = "home/telemetry/api";
    
    @Inject
    private MqttClientManager mqttClientManager;
    
    @POST
    @Path("/publish")
    @Consumes(MediaType.TEXT_PLAIN)
    public Response publishTelemetry(String payload) {
        // Облако работает только в режиме мониторинга: публикация отключена
        logger.warn("/telemetry/publish is disabled in monitoring-only mode");
        return Response.status(Response.Status.FORBIDDEN)
                .entity("{\"error\": \"Publishing disabled in monitoring-only mode\"}")
                .build();
    }
    
    @GET
    @Path("/status")
    public Response getStatus() {
        return Response.ok()
                .entity("{\"status\": \"MQTT backend is running\"}")
                .build();
    }
}

