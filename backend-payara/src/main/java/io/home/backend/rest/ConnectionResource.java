package io.home.backend.rest;

import io.home.registry.model.DeviceConnection;
import io.home.registry.repository.ConnectionRepository;
import io.home.registry.DatabaseManager;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Path("/connections")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
public class ConnectionResource {
    
    private static final Logger logger = LoggerFactory.getLogger(ConnectionResource.class);
    
    @Inject
    private DatabaseManager databaseManager;
    
    @GET
    @Path("/active")
    public Response getActiveConnections() {
        try {
            ConnectionRepository connectionRepository = new ConnectionRepository(databaseManager);
            List<DeviceConnection> activeConnections = connectionRepository.findActiveConnections();
            return Response.ok(activeConnections).build();
        } catch (Exception e) {
            logger.error("Error getting active connections", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("{\"error\": \"" + e.getMessage() + "\"}").build();
        }
    }
    
    @GET
    @Path("/device/{deviceId}")
    public Response getConnectionsByDevice(@PathParam("deviceId") Long deviceId) {
        try {
            ConnectionRepository connectionRepository = new ConnectionRepository(databaseManager);
            List<DeviceConnection> connections = connectionRepository.findActiveByDeviceId(deviceId)
                .map(List::of)
                .orElse(List.of());
            
            return Response.ok(connections).build();
        } catch (Exception e) {
            logger.error("Error getting connections for device: {}", deviceId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("{\"error\": \"" + e.getMessage() + "\"}").build();
        }
    }
    
    @POST
    @Path("/device/{deviceId}/close")
    public Response closeDeviceConnections(@PathParam("deviceId") Long deviceId) {
        try {
            ConnectionRepository connectionRepository = new ConnectionRepository(databaseManager);
            connectionRepository.closeAllConnectionsForDevice(deviceId);
            
            return Response.ok("{\"message\": \"All connections for device closed successfully\"}").build();
        } catch (Exception e) {
            logger.error("Error closing connections for device: {}", deviceId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("{\"error\": \"" + e.getMessage() + "\"}").build();
        }
    }
}

