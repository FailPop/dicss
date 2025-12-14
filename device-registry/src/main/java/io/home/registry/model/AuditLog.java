package io.home.registry.model;

import java.time.LocalDateTime;

public class AuditLog {
    private long id;
    private String eventType;
    private String subject;
    private String details;
    private LocalDateTime createdAt;

    public AuditLog(String eventType, String subject, String details) {
        this.eventType = eventType;
        this.subject = subject;
        this.details = details;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getEventType() { return eventType; }
    public String getSubject() { return subject; }
    public String getDetails() { return details; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}


