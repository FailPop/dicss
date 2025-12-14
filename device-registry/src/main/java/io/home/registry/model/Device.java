package io.home.registry.model;

import java.time.LocalDateTime;

public class Device {
    private Long id;
    private String deviceType;
    private String serialHash;
    private String macHash;
    private String compositeHash;
    private String status;
    private boolean critical;
    private LocalDateTime registeredAt;
    private LocalDateTime approvedAt;
    private String approvedBy;
    private LocalDateTime lastHealthCheck;
    
    public Device() {}
    
    public Device(String deviceType, String serialHash, String macHash, String compositeHash) {
        this.deviceType = deviceType;
        this.serialHash = serialHash;
        this.macHash = macHash;
        this.compositeHash = compositeHash;
        this.status = "PENDING";
        this.critical = false;
    }
    
    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
    
    public String getSerialHash() { return serialHash; }
    public void setSerialHash(String serialHash) { this.serialHash = serialHash; }
    
    public String getMacHash() { return macHash; }
    public void setMacHash(String macHash) { this.macHash = macHash; }
    
    public String getCompositeHash() { return compositeHash; }
    public void setCompositeHash(String compositeHash) { this.compositeHash = compositeHash; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public boolean isCritical() { return critical; }
    public void setCritical(boolean critical) { this.critical = critical; }
    
    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(LocalDateTime registeredAt) { this.registeredAt = registeredAt; }
    
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    
    public LocalDateTime getLastHealthCheck() { return lastHealthCheck; }
    public void setLastHealthCheck(LocalDateTime lastHealthCheck) { this.lastHealthCheck = lastHealthCheck; }
}
