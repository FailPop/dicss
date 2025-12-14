CREATE TABLE IF NOT EXISTS devices (
    id BIGSERIAL PRIMARY KEY,
    device_type VARCHAR(50) NOT NULL,
    serial_hash VARCHAR(64) NOT NULL,
    mac_hash VARCHAR(64) NOT NULL,
    composite_hash VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'BLOCKED')),
    is_critical BOOLEAN DEFAULT FALSE,
    registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    approved_at TIMESTAMP NULL,
    approved_by VARCHAR(100) NULL,
    last_health_check TIMESTAMP NULL
);

CREATE TABLE IF NOT EXISTS device_connections (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT NOT NULL,
    connected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    disconnected_at TIMESTAMP NULL,
    ip_address VARCHAR(45),
    client_info TEXT,
    FOREIGN KEY (device_id) REFERENCES devices(id)
);

CREATE TABLE IF NOT EXISTS security_alerts (
    id BIGSERIAL PRIMARY KEY,
    alert_type VARCHAR(50) NOT NULL,
    device_serial_hash VARCHAR(64),
    details TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_composite_hash ON devices(composite_hash);
CREATE INDEX IF NOT EXISTS idx_serial_hash ON devices(serial_hash);
CREATE INDEX IF NOT EXISTS idx_device_status ON devices(status);
CREATE INDEX IF NOT EXISTS idx_active_connections ON device_connections(device_id, disconnected_at);

-- Telemetry storage
CREATE TABLE IF NOT EXISTS telemetry (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT NOT NULL,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    topic VARCHAR(255) NOT NULL,
    ts TIMESTAMP NULL,
    measurement VARCHAR(100) NULL,
    metric_value DOUBLE PRECISION NULL,
    payload_raw TEXT,
    FOREIGN KEY (device_id) REFERENCES devices(id)
);

CREATE INDEX IF NOT EXISTS idx_telemetry_device_time ON telemetry(device_id, received_at);

-- Client bindings (pairing)
CREATE TABLE IF NOT EXISTS client_bindings (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(64) NOT NULL UNIQUE,
    fingerprint VARCHAR(128) NOT NULL,
    role VARCHAR(32) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMP NULL
);

-- Audit logs
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    subject VARCHAR(128) NULL,
    details TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- removed duplicate telemetry definition
