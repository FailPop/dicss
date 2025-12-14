package io.home.registry.model;

import java.time.LocalDateTime;

public class DeviceConnection {
    private Long id;
    private Long deviceId;
    private LocalDateTime connectedAt;
    private LocalDateTime disconnectedAt;
    private String ipAddress;
    private String clientInfo;
    
    public DeviceConnection() {}
    
    public DeviceConnection(Long deviceId, String ipAddress, String clientInfo) {
        this.deviceId = deviceId;
        this.ipAddress = ipAddress;
        this.clientInfo = clientInfo;
        this.connectedAt = LocalDateTime.now();
    }
    
    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    
    public LocalDateTime getConnectedAt() { return connectedAt; }
    public void setConnectedAt(LocalDateTime connectedAt) { this.connectedAt = connectedAt; }
    
    public LocalDateTime getDisconnectedAt() { return disconnectedAt; }
    public void setDisconnectedAt(LocalDateTime disconnectedAt) { this.disconnectedAt = disconnectedAt; }
    
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    
    public String getClientInfo() { return clientInfo; }
    public void setClientInfo(String clientInfo) { this.clientInfo = clientInfo; }
    
    public boolean isActive() {
        return disconnectedAt == null;
    }
}

