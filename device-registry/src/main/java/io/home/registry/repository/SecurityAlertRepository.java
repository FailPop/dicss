package io.home.registry.repository;

import io.home.registry.DatabaseManager;
import io.home.registry.model.SecurityAlert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SecurityAlertRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(SecurityAlertRepository.class);
    
    private final DatabaseManager dbManager;
    
    public SecurityAlertRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }
    
    public SecurityAlert createAlert(SecurityAlert alert) {
        String sql = "INSERT INTO security_alerts (alert_type, device_serial_hash, details, created_at) VALUES (?, ?, ?, ?)";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setString(1, alert.getAlertType());
            stmt.setString(2, alert.getDeviceSerialHash());
            stmt.setString(3, alert.getDetails());
            stmt.setTimestamp(4, Timestamp.valueOf(alert.getCreatedAt()));
            
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating alert failed, no rows affected.");
            }
            
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    alert.setId(generatedKeys.getLong(1));
                } else {
                    throw new SQLException("Creating alert failed, no ID obtained.");
                }
            }
            
            logger.info("Security alert created with ID: {} - Type: {}", alert.getId(), alert.getAlertType());
            return alert;
            
        } catch (SQLException e) {
            logger.error("Failed to create security alert", e);
            throw new RuntimeException("Database operation failed", e);
        }
    }
    
    public List<SecurityAlert> findAll() {
        String sql = "SELECT * FROM security_alerts ORDER BY created_at DESC";
        List<SecurityAlert> alerts = new ArrayList<>();
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                alerts.add(mapResultSetToAlert(rs));
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find all security alerts", e);
            throw new RuntimeException("Database operation failed", e);
        }
        
        return alerts;
    }
    
    public List<SecurityAlert> findByDeviceSerialHash(String deviceSerialHash) {
        String sql = "SELECT * FROM security_alerts WHERE device_serial_hash = ? ORDER BY created_at DESC";
        List<SecurityAlert> alerts = new ArrayList<>();
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, deviceSerialHash);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    alerts.add(mapResultSetToAlert(rs));
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find alerts by device serial hash", e);
            throw new RuntimeException("Database operation failed", e);
        }
        
        return alerts;
    }
    
    public List<SecurityAlert> findByAlertType(String alertType) {
        String sql = "SELECT * FROM security_alerts WHERE alert_type = ? ORDER BY created_at DESC";
        List<SecurityAlert> alerts = new ArrayList<>();
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, alertType);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    alerts.add(mapResultSetToAlert(rs));
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find alerts by type", e);
            throw new RuntimeException("Database operation failed", e);
        }
        
        return alerts;
    }
    
    private SecurityAlert mapResultSetToAlert(ResultSet rs) throws SQLException {
        SecurityAlert alert = new SecurityAlert();
        alert.setId(rs.getLong("id"));
        alert.setAlertType(rs.getString("alert_type"));
        alert.setDeviceSerialHash(rs.getString("device_serial_hash"));
        alert.setDetails(rs.getString("details"));
        
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            alert.setCreatedAt(createdAt.toLocalDateTime());
        }
        
        return alert;
    }
}

