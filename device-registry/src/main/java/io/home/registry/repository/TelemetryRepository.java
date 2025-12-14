package io.home.registry.repository;

import io.home.registry.DatabaseManager;
import io.home.registry.model.Telemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

public class TelemetryRepository {

    private static final Logger logger = LoggerFactory.getLogger(TelemetryRepository.class);

    private final DatabaseManager dbManager;

    public TelemetryRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public Telemetry insert(Telemetry telemetry) {
        String sql = "INSERT INTO telemetry (device_id, received_at, topic, ts, measurement, metric_value, payload_raw) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setLong(1, telemetry.getDeviceId());
            stmt.setTimestamp(2, Timestamp.valueOf(telemetry.getReceivedAt() != null ? telemetry.getReceivedAt() : java.time.LocalDateTime.now()));
            stmt.setString(3, telemetry.getTopic());
            if (telemetry.getTs() != null) {
                stmt.setTimestamp(4, Timestamp.valueOf(telemetry.getTs()));
            } else {
                stmt.setNull(4, Types.TIMESTAMP);
            }
            stmt.setString(5, telemetry.getMeasurement());
            if (telemetry.getValue() != null) {
                stmt.setDouble(6, telemetry.getValue());
            } else {
                stmt.setNull(6, Types.DOUBLE);
            }
            stmt.setString(7, telemetry.getPayloadRaw());

            int affected = stmt.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Inserting telemetry failed, no rows affected.");
            }

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    telemetry.setId(keys.getLong(1));
                }
            }
            logger.debug("Telemetry inserted id={}", telemetry.getId());
            return telemetry;
        } catch (SQLException e) {
            logger.error("Failed to insert telemetry", e);
            throw new RuntimeException("Database operation failed", e);
        }
    }
}

 


