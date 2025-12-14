package io.home.registry.repository;

import io.home.registry.DatabaseManager;
import io.home.registry.model.AuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

public class AuditLogRepository {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogRepository.class);

    private final DatabaseManager dbManager;

    public AuditLogRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public AuditLog insert(AuditLog log) {
        String sql = "INSERT INTO audit_logs (event_type, subject, details) VALUES (?,?,?)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, log.getEventType());
            ps.setString(2, log.getSubject());
            ps.setString(3, log.getDetails());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) log.setId(rs.getLong(1));
            }
            logger.debug("Audit log inserted id={}", log.getId());
            return log;
        } catch (SQLException e) {
            logger.error("Failed to insert audit log", e);
            throw new RuntimeException(e);
        }
    }
}


