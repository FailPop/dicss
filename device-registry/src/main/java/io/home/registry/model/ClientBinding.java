package io.home.registry.model;

import java.time.LocalDateTime;

public class ClientBinding {
    private long id;
    private String uuid;
    private String fingerprint;
    private String role;
    private LocalDateTime createdAt;
    private LocalDateTime lastSeenAt;

    public ClientBinding(String uuid, String fingerprint, String role) {
        this.uuid = uuid;
        this.fingerprint = fingerprint;
        this.role = role;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getUuid() { return uuid; }
    public String getFingerprint() { return fingerprint; }
    public String getRole() { return role; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(LocalDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }
}


