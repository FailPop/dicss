package io.home.registry.model;

import java.time.LocalDateTime;

public class SecurityAlert {
    private Long id;
    private String alertType;
    private String deviceSerialHash;
    private String details;
    private LocalDateTime createdAt;
    
    public SecurityAlert() {}
    
    public SecurityAlert(String alertType, String deviceSerialHash, String details) {
        this.alertType = alertType;
        this.deviceSerialHash = deviceSerialHash;
        this.details = details;
        this.createdAt = LocalDateTime.now();
    }
    
    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }
    
    public String getDeviceSerialHash() { return deviceSerialHash; }
    public void setDeviceSerialHash(String deviceSerialHash) { this.deviceSerialHash = deviceSerialHash; }
    
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

