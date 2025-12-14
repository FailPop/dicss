package io.home.broker.auth;

import io.home.registry.DeviceIdentityHasher;
import io.home.registry.model.Device;
import io.moquette.broker.security.IAuthorizatorPolicy;
import io.moquette.broker.subscriptions.Topic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class DeviceAuthorizatorPolicy implements IAuthorizatorPolicy {

    private static final Logger logger = LoggerFactory.getLogger(DeviceAuthorizatorPolicy.class);
    private static final String CONTROLLER_CLIENT_ID = "controller-cmd";
    
    private final DeviceAuthenticator deviceAuthenticator;

    public DeviceAuthorizatorPolicy(DeviceAuthenticator deviceAuthenticator) {
        this.deviceAuthenticator = deviceAuthenticator;
    }

    @Override
    public boolean canWrite(Topic topic, String user, String client) {
        if (client == null || topic == null) {
            return false;
        }
        String topicStr = topic.toString();
        
        // Controller and admin clients have full access
        if (CONTROLLER_CLIENT_ID.equals(client) || client.startsWith("ADMIN_")) {
            return true;
        }
        
        // Devices (IOT clients)
        if (client.startsWith("IOT")) {
            String controllerId = System.getProperty("controller.id", "controller-01");
            String topicPrefix = "home/" + controllerId + "/devices/";
            
            // Security: Devices cannot write to command topics
            if (topicStr.endsWith("/cmd")) {
                logger.warn("Device {} attempted to write to command topic: {}", client, topicStr);
                return false;
            }
            
            // Allow devices to write only to their own telemetry/register/health topics
            if (topicStr.startsWith(topicPrefix) && 
                (topicStr.endsWith("/telemetry") || topicStr.endsWith("/register") || topicStr.endsWith("/health"))) {
                
                // Extract deviceId from topic: home/{controllerId}/devices/{deviceId}/telemetry
                try {
                    String[] parts = topicStr.split("/");
                    if (parts.length >= 4) {
                        String deviceSerial = parts[3]; // deviceId is serial in topic
                        
                        // Validate device exists and has APPROVED status
                        String serialHash = DeviceIdentityHasher.hash(deviceSerial);
                        Optional<Device> deviceOpt = deviceAuthenticator.getDeviceRepository().findBySerialHash(serialHash);
                        
                        if (deviceOpt.isEmpty()) {
                            logger.warn("Device not found in registry for topic write: client={}, topic={}", client, topicStr);
                            return false;
                        }
                        
                        Device device = deviceOpt.get();
                        
                        // Only APPROVED devices can publish
                        if (!"APPROVED".equals(device.getStatus())) {
                            logger.warn("Non-approved device attempted to publish: client={}, topic={}, status={}", 
                                client, topicStr, device.getStatus());
                            return false;
                        }
                        
                        // Additional validation: verify clientId matches device (extract serial from clientId)
                        try {
                            String clientSerialSuffix = client.substring(3, 7); // Last 4 digits from IOTxxxx
                            String expectedSerialSuffix = deviceSerial.substring(deviceSerial.length() - 4);
                            
                            if (!clientSerialSuffix.equals(expectedSerialSuffix)) {
                                logger.warn("ClientId serial mismatch: client={}, topic device={}", client, deviceSerial);
                                return false;
                            }
                        } catch (Exception e) {
                            logger.debug("Could not validate serial from clientId: {}", e.getMessage());
                            // Continue - this is a best-effort check
                        }
                        
                        return true;
                    }
                } catch (Exception e) {
                    logger.error("Error validating device for topic write: client={}, topic={}", client, topicStr, e);
                    return false;
                }
            }
            
            // No writes to other topics
            return false;
        }
        
        return false;
    }

    @Override
    public boolean canRead(Topic topic, String user, String client) {
        if (client == null || topic == null) {
            return false;
        }
        String topicStr = topic.toString();
        
        // Wildcard topics only for admins
        if (topicStr.contains("#")) {
            return client.startsWith("ADMIN_");
        }
        
        // Devices (IOT clients) - can only read their command topic
        if (client.startsWith("IOT")) {
            String controllerId = System.getProperty("controller.id", "controller-01");
            String topicPrefix = "home/" + controllerId + "/devices/";
            
            if (topicStr.startsWith(topicPrefix) && topicStr.endsWith("/cmd")) {
                // Extract deviceId from topic and validate
                try {
                    String[] parts = topicStr.split("/");
                    if (parts.length >= 4) {
                        String deviceSerial = parts[3];
                        String serialHash = DeviceIdentityHasher.hash(deviceSerial);
                        Optional<Device> deviceOpt = deviceAuthenticator.getDeviceRepository().findBySerialHash(serialHash);
                        
                        if (deviceOpt.isPresent()) {
                            Device device = deviceOpt.get();
                            // Only APPROVED devices can subscribe to commands
                            return "APPROVED".equals(device.getStatus());
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error validating device for topic read: client={}, topic={}", client, topicStr, e);
                }
                return false;
            }
            
            // Devices cannot read other topics
            return false;
        }
        
        // Controller and admins have read access
        return true;
    }
}


