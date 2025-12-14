package io.home.backend.rest;

import io.home.registry.DatabaseManager;
import io.home.registry.model.Device;
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

@Path("/devices")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
public class DeviceResource {
    
    private static final Logger logger = LoggerFactory.getLogger(DeviceResource.class);
    
    @Inject
    private AdminService adminService;
    
    @GET
    public Response getAllDevices() {
        try {
            List<Device> devices = adminService.getAllDevices();
            return Response.ok(devices).build();
        } catch (Exception e) {
            logger.error("Error getting all devices", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("{\"error\": \"" + e.getMessage() + "\"}").build();
        }
    }
    
    @GET
    @Path("/{id}")
    public Response getDevice(@PathParam("id") Long deviceId) {
        try {
            List<Device> allDevices = adminService.getAllDevices();
            Device device = allDevices.stream()
                .filter(d -> d.getId().equals(deviceId))
                .findFirst()
                .orElse(null);
            
            if (device == null) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\": \"Device not found\"}").build();
            }
            
            return Response.ok(device).build();
        } catch (Exception e) {
            logger.error("Error getting device: {}", deviceId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("{\"error\": \"" + e.getMessage() + "\"}").build();
        }
    }
    
    @GET
    @Path("/pending")
    public Response getPendingDevices() {
        try {
            List<Device> pendingDevices = adminService.getPendingDevices();
            return Response.ok(pendingDevices).build();
        } catch (Exception e) {
            logger.error("Error getting pending devices", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("{\"error\": \"" + e.getMessage() + "\"}").build();
        }
    }
    
    @POST
    @Path("/{id}/approve")
    public Response approveDevice(@PathParam("id") Long deviceId, 
                                 @QueryParam("admin") @DefaultValue("admin") String adminName) {
        try {
            boolean success = adminService.approveDevice(deviceId, adminName);
            if (success) {
                return Response.ok("{\"message\": \"Device approved successfully\"}").build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Failed to approve device\"}").build();
            }
        } catch (Exception e) {
            logger.error("Error approving device: {}", deviceId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("{\"error\": \"" + e.getMessage() + "\"}").build();
        }
    }
    
    @POST
    @Path("/{id}/reject")
    public Response rejectDevice(@PathParam("id") Long deviceId,
                                @QueryParam("admin") @DefaultValue("admin") String adminName,
                                @QueryParam("reason") @DefaultValue("No reason provided") String reason) {
        try {
            boolean success = adminService.rejectDevice(deviceId, adminName, reason);
            if (success) {
                return Response.ok("{\"message\": \"Device rejected successfully\"}").build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Failed to reject device\"}").build();
            }
        } catch (Exception e) {
            logger.error("Error rejecting device: {}", deviceId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("{\"error\": \"" + e.getMessage() + "\"}").build();
        }
    }
    
    @POST
    @Path("/{id}/unblock")
    public Response unblockDevice(@PathParam("id") Long deviceId,
                                 @QueryParam("admin") @DefaultValue("admin") String adminName) {
        try {
            boolean success = adminService.unblockDevice(deviceId, adminName);
            if (success) {
                return Response.ok("{\"message\": \"Device unblocked successfully\"}").build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Failed to unblock device\"}").build();
            }
        } catch (Exception e) {
            logger.error("Error unblocking device: {}", deviceId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("{\"error\": \"" + e.getMessage() + "\"}").build();
        }
    }
    
    @POST
    @Path("/{id}/critical")
    public Response markDeviceAsCritical(@PathParam("id") Long deviceId,
                                        @QueryParam("admin") @DefaultValue("admin") String adminName) {
        try {
            boolean success = adminService.markDeviceAsCritical(deviceId, adminName);
            if (success) {
                return Response.ok("{\"message\": \"Device marked as critical successfully\"}").build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Failed to mark device as critical\"}").build();
            }
        } catch (Exception e) {
            logger.error("Error marking device as critical: {}", deviceId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("{\"error\": \"" + e.getMessage() + "\"}").build();
        }
    }
}

