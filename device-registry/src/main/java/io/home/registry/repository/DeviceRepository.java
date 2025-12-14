package io.home.registry.repository;

import io.home.registry.DatabaseManager;
import io.home.registry.model.Device;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DeviceRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(DeviceRepository.class);
    
    private final DatabaseManager dbManager;
    
    public DeviceRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }
    
    public Device insert(Device device) {
        String sql = "INSERT INTO devices (device_type, serial_hash, mac_hash, composite_hash, status, is_critical, registered_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        // getConnection() returns a new connection each time (with DB_CLOSE_ON_EXIT=FALSE)
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setString(1, device.getDeviceType());
            stmt.setString(2, device.getSerialHash());
            stmt.setString(3, device.getMacHash());
            stmt.setString(4, device.getCompositeHash());
            stmt.setString(5, device.getStatus());
            stmt.setBoolean(6, device.isCritical());
            stmt.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
            
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating device failed, no rows affected.");
            }
            
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    device.setId(generatedKeys.getLong(1));
                } else {
                    throw new SQLException("Creating device failed, no ID obtained.");
                }
            }
            
            logger.info("Device inserted with ID: {}", device.getId());
            return device;
            
        } catch (SQLException e) {
            logger.error("Failed to insert device", e);
            throw new RuntimeException("Database operation failed", e);
        }
    }

    public Optional<Device> upsertIfNotExists(Device device) {
        // Check by composite_hash
        Optional<Device> existing = findByCompositeHash(device.getCompositeHash());
        if (existing.isPresent()) {
            return existing;
        }
        return Optional.of(insert(device));
    }
    
    public Optional<Device> findByCompositeHash(String compositeHash) {
        String sql = "SELECT * FROM devices WHERE composite_hash = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, compositeHash);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToDevice(rs));
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find device by composite hash", e);
            throw new RuntimeException("Database operation failed", e);
        }
        
        return Optional.empty();
    }
    
    public Optional<Device> findBySerialHash(String serialHash) {
        String sql = "SELECT * FROM devices WHERE serial_hash = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, serialHash);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToDevice(rs));
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find device by serial hash", e);
            throw new RuntimeException("Database operation failed", e);
        }
        
        return Optional.empty();
    }
    
    public Optional<Device> findById(Long deviceId) {
        String sql = "SELECT * FROM devices WHERE id = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, deviceId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToDevice(rs));
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find device by ID: {}", deviceId, e);
            throw new RuntimeException("Database operation failed", e);
        }
        
        return Optional.empty();
    }
    
    public void updateStatus(Long deviceId, String status, String approvedBy) {
        // Use transaction with SELECT FOR UPDATE to prevent race conditions
        String selectSql = "SELECT id, status FROM devices WHERE id = ? FOR UPDATE";
        String updateSql = "UPDATE devices SET status = ?, approved_at = ?, approved_by = ? WHERE id = ?";
        
        Connection conn = null;
        try {
            conn = dbManager.getConnection();
            conn.setAutoCommit(false); // Start transaction
            
            // Lock row for update to prevent race conditions
            try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
                selectStmt.setLong(1, deviceId);
                try (var rs = selectStmt.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        logger.warn("No device found with ID: {}", deviceId);
                        return;
                    }
                    String currentStatus = rs.getString("status");
                    logger.debug("Updating device {} status from {} to {}", deviceId, currentStatus, status);
                }
            }
            
            // Update status
            try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                updateStmt.setString(1, status);
                updateStmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
                updateStmt.setString(3, approvedBy);
                updateStmt.setLong(4, deviceId);
                
                int affectedRows = updateStmt.executeUpdate();
                if (affectedRows == 0) {
                    conn.rollback();
                    logger.warn("No device found with ID: {} (after lock)", deviceId);
                    return;
                }
            }
            
            conn.commit();
            logger.info("Device {} status updated to: {} (transaction committed)", deviceId, status);
            
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    logger.error("Failed to rollback transaction", rollbackEx);
                }
            }
            logger.error("Failed to update device status", e);
            throw new RuntimeException("Database operation failed", e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException e) {
                    logger.error("Failed to reset auto-commit", e);
                }
            }
        }
    }
    
    public void markAsCritical(Long deviceId) {
        String sql = "UPDATE devices SET is_critical = TRUE WHERE id = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, deviceId);
            
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                logger.warn("No device found with ID: {}", deviceId);
            } else {
                logger.info("Device {} marked as critical", deviceId);
            }
            
        } catch (SQLException e) {
            logger.error("Failed to mark device as critical", e);
            throw new RuntimeException("Database operation failed", e);
        }
    }
    
    public List<Device> findByStatus(String status) {
        String sql = "SELECT * FROM devices WHERE status = ? ORDER BY registered_at DESC";
        List<Device> devices = new ArrayList<>();
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, status);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    devices.add(mapResultSetToDevice(rs));
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find devices by status", e);
            throw new RuntimeException("Database operation failed", e);
        }
        
        return devices;
    }
    
    public List<Device> findAll() {
        String sql = "SELECT * FROM devices ORDER BY registered_at DESC";
        List<Device> devices = new ArrayList<>();
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                devices.add(mapResultSetToDevice(rs));
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find all devices", e);
            throw new RuntimeException("Database operation failed", e);
        }
        
        return devices;
    }
    
    private Device mapResultSetToDevice(ResultSet rs) throws SQLException {
        Device device = new Device();
        device.setId(rs.getLong("id"));
        device.setDeviceType(rs.getString("device_type"));
        device.setSerialHash(rs.getString("serial_hash"));
        device.setMacHash(rs.getString("mac_hash"));
        device.setCompositeHash(rs.getString("composite_hash"));
        device.setStatus(rs.getString("status"));
        device.setCritical(rs.getBoolean("is_critical"));
        
        Timestamp registeredAt = rs.getTimestamp("registered_at");
        if (registeredAt != null) {
            device.setRegisteredAt(registeredAt.toLocalDateTime());
        }
        
        Timestamp approvedAt = rs.getTimestamp("approved_at");
        if (approvedAt != null) {
            device.setApprovedAt(approvedAt.toLocalDateTime());
        }
        
        device.setApprovedBy(rs.getString("approved_by"));
        
        Timestamp lastHealthCheck = rs.getTimestamp("last_health_check");
        if (lastHealthCheck != null) {
            device.setLastHealthCheck(lastHealthCheck.toLocalDateTime());
        }
        
        return device;
    }
    
    public void updateLastHealthCheck(Long deviceId) {
        String sql = "UPDATE devices SET last_health_check = ? WHERE id = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setLong(2, deviceId);
            
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                logger.warn("No device found with ID: {}", deviceId);
            } else {
                logger.debug("Device {} last health check updated", deviceId);
            }
            
        } catch (SQLException e) {
            logger.error("Failed to update last health check", e);
            throw new RuntimeException("Database operation failed", e);
        }
    }
    
    public void clearAllDevices() {
        // Delete in order to respect foreign key constraints
        // Ignore errors if tables don't exist yet
        try (Connection conn = dbManager.getConnection()) {
            conn.setAutoCommit(true); // Use auto-commit for individual operations
            
            // Delete dependent records first (ignore if table doesn't exist)
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM telemetry")) {
                int telemetryDeleted = stmt.executeUpdate();
                logger.info("Deleted {} telemetry records", telemetryDeleted);
            } catch (SQLException e) {
                String errorMsg = e.getMessage();
                if (!errorMsg.contains("не существует") && !errorMsg.contains("does not exist")) {
                    logger.warn("Error deleting telemetry (table may not exist): {}", errorMsg);
                } else {
                    logger.debug("Table telemetry does not exist, skipping");
                }
            }
            
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM device_connections")) {
                int connectionsDeleted = stmt.executeUpdate();
                logger.info("Deleted {} device connections", connectionsDeleted);
            } catch (SQLException e) {
                String errorMsg = e.getMessage();
                if (!errorMsg.contains("не существует") && !errorMsg.contains("does not exist")) {
                    logger.warn("Error deleting device_connections (table may not exist): {}", errorMsg);
                } else {
                    logger.debug("Table device_connections does not exist, skipping");
                }
            }
            
            // Note: security_alerts doesn't have FK to devices, so it's safe to leave or delete separately
            // Delete devices last
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM devices")) {
                int devicesDeleted = stmt.executeUpdate();
                logger.info("Deleted {} devices", devicesDeleted);
            } catch (SQLException e) {
                String errorMsg = e.getMessage();
                if (!errorMsg.contains("не существует") && !errorMsg.contains("does not exist")) {
                    logger.warn("Error deleting devices (table may not exist): {}", errorMsg);
                } else {
                    logger.debug("Table devices does not exist, skipping");
                }
            }
            
            logger.info("All devices and related data cleared successfully");
            
        } catch (SQLException e) {
            logger.error("Failed to clear all devices", e);
            // Don't throw - allow initialization to continue even if clearing fails
            logger.warn("Continuing with device initialization despite clear error");
        }
    }
}
