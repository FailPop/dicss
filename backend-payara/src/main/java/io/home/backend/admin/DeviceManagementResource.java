package io.home.backend.admin;

import io.home.registry.DatabaseManager;
import io.home.registry.model.Device;
import io.home.registry.model.SecurityAlert;
import io.home.registry.repository.DeviceRepository;
import io.home.registry.repository.SecurityAlertRepository;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Path("/admin")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class DeviceManagementResource {
    
    private static final Logger logger = LoggerFactory.getLogger(DeviceManagementResource.class);
    
    @Inject
    private DatabaseManager dbManager;
    
    private DeviceRepository deviceRepository;
    private SecurityAlertRepository alertRepository;
    
    @PostConstruct
    public void init() {
        try {
            deviceRepository = new DeviceRepository(dbManager);
            alertRepository = new SecurityAlertRepository(dbManager);
            logger.info("Device management resource initialized with injected DatabaseManager");
        } catch (Exception e) {
            logger.error("Failed to initialize device management resource", e);
        }
    }
    
    @GET
    @Path("/devices/pending")
    public Response getPendingDevices() {
        try {
            List<Device> pendingDevices = deviceRepository.findByStatus("PENDING");
            return Response.ok(pendingDevices).build();
        } catch (Exception e) {
            logger.error("Error getting pending devices", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}")
                    .build();
        }
    }
    
    @GET
    @Path("/devices")
    public Response getAllDevices() {
        try {
            List<Device> devices = deviceRepository.findAll();
            return Response.ok(devices).build();
        } catch (Exception e) {
            logger.error("Error getting all devices", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}")
                    .build();
        }
    }
    
    @POST
    @Path("/devices/{id}/approve")
    public Response approveDevice(@PathParam("id") Long deviceId, 
                                 @QueryParam("approvedBy") String approvedBy) {
        try {
            // Validation
            if (deviceId == null || deviceId <= 0) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"error\": \"Invalid device ID\"}").build();
            }
            if (approvedBy == null || approvedBy.trim().isEmpty()) {
                approvedBy = "admin";
            }
            
            deviceRepository.updateStatus(deviceId, "APPROVED", approvedBy);
            
            logger.info("Device {} approved by {}", deviceId, approvedBy);
            return Response.ok()
                    .entity("{\"status\": \"approved\", \"deviceId\": " + deviceId + ", \"approvedBy\": \"" + approvedBy + "\"}")
                    .build();
                    
        } catch (Exception e) {
            logger.error("Error approving device {}", deviceId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}")
                    .build();
        }
    }
    
    @POST
    @Path("/devices/{id}/reject")
    public Response rejectDevice(@PathParam("id") Long deviceId,
                               @QueryParam("rejectedBy") String rejectedBy) {
        try {
            // Validation
            if (deviceId == null || deviceId <= 0) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"error\": \"Invalid device ID\"}").build();
            }
            if (rejectedBy == null || rejectedBy.trim().isEmpty()) {
                rejectedBy = "admin";
            }
            
            deviceRepository.updateStatus(deviceId, "REJECTED", rejectedBy);
            
            logger.info("Device {} rejected by {}", deviceId, rejectedBy);
            return Response.ok()
                    .entity("{\"status\": \"rejected\", \"deviceId\": " + deviceId + ", \"rejectedBy\": \"" + rejectedBy + "\"}")
                    .build();
                    
        } catch (Exception e) {
            logger.error("Error rejecting device {}", deviceId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}")
                    .build();
        }
    }
    
    @POST
    @Path("/devices/{id}/mark-critical")
    public Response markDeviceAsCritical(@PathParam("id") Long deviceId) {
        try {
            deviceRepository.markAsCritical(deviceId);
            
            logger.info("Device {} marked as critical", deviceId);
            return Response.ok()
                    .entity("{\"status\": \"marked_critical\", \"deviceId\": " + deviceId + "}")
                    .build();
                    
        } catch (Exception e) {
            logger.error("Error marking device {} as critical", deviceId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}")
                    .build();
        }
    }
    
    @GET
    @Path("/security/alerts")
    public Response getSecurityAlerts() {
        try {
            List<SecurityAlert> alerts = alertRepository.findAll();
            return Response.ok(alerts).build();
        } catch (Exception e) {
            logger.error("Error getting security alerts", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}")
                    .build();
        }
    }
    
    @GET
    @Path("/security/alerts/{type}")
    public Response getSecurityAlertsByType(@PathParam("type") String alertType) {
        try {
            List<SecurityAlert> alerts = alertRepository.findByAlertType(alertType);
            return Response.ok(alerts).build();
        } catch (Exception e) {
            logger.error("Error getting security alerts by type: {}", alertType, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}")
                    .build();
        }
    }
    
    @GET
    @Path("/status")
    public Response getAdminStatus() {
        return Response.ok()
                .entity("{\"status\": \"admin_api_running\", \"timestamp\": " + System.currentTimeMillis() + "}")
                .build();
    }
}

