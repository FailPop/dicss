package io.home.registry.model;

import java.time.LocalDateTime;

public class Telemetry {
    private long id;
    private long deviceId;
    private LocalDateTime receivedAt;
    private String topic;
    private LocalDateTime ts;
    private String measurement;
    private Double value;
    private String payloadRaw;

    public Telemetry(long deviceId, String topic, LocalDateTime ts, String measurement, Double value, String payloadRaw) {
        this.deviceId = deviceId;
        this.topic = topic;
        this.ts = ts;
        this.measurement = measurement;
        this.value = value;
        this.payloadRaw = payloadRaw;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getDeviceId() { return deviceId; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }
    public String getTopic() { return topic; }
    public LocalDateTime getTs() { return ts; }
    public String getMeasurement() { return measurement; }
    public Double getValue() { return value; }
    public String getPayloadRaw() { return payloadRaw; }
}


