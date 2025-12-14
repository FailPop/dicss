package io.home.backend.rest;

import io.home.registry.model.SecurityAlert;
import io.home.registry.service.AdminService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Path("/alerts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
public class AlertResource {
    
    private static final Logger logger = LoggerFactory.getLogger(AlertResource.class);
    
    @Inject
    private AdminService adminService;
    
    @GET
    public Response getAllAlerts() {
        try {
            List<SecurityAlert> alerts = adminService.getAllAlerts();
            return Response.ok(alerts).build();
        } catch (Exception e) {
            logger.error("Error getting all alerts", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("{\"error\": \"" + e.getMessage() + "\"}").build();
        }
    }
    
    @GET
    @Path("/device/{serialHash}")
    public Response getAlertsByDevice(@PathParam("serialHash") String deviceSerialHash) {
        try {
            List<SecurityAlert> alerts = adminService.getAlertsByDevice(deviceSerialHash);
            return Response.ok(alerts).build();
        } catch (Exception e) {
            logger.error("Error getting alerts for device: {}", deviceSerialHash, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("{\"error\": \"" + e.getMessage() + "\"}").build();
        }
    }
    
    @GET
    @Path("/type/{alertType}")
    public Response getAlertsByType(@PathParam("alertType") String alertType) {
        try {
            List<SecurityAlert> allAlerts = adminService.getAllAlerts();
            List<SecurityAlert> filteredAlerts = allAlerts.stream()
                .filter(alert -> alert.getAlertType().equals(alertType))
                .toList();
            
            return Response.ok(filteredAlerts).build();
        } catch (Exception e) {
            logger.error("Error getting alerts by type: {}", alertType, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("{\"error\": \"" + e.getMessage() + "\"}").build();
        }
    }
    
    @GET
    @Path("/recent")
    public Response getRecentAlerts(@QueryParam("limit") @DefaultValue("10") int limit) {
        try {
            List<SecurityAlert> allAlerts = adminService.getAllAlerts();
            List<SecurityAlert> recentAlerts = allAlerts.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(limit)
                .toList();
            
            return Response.ok(recentAlerts).build();
        } catch (Exception e) {
            logger.error("Error getting recent alerts", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("{\"error\": \"" + e.getMessage() + "\"}").build();
        }
    }
}

