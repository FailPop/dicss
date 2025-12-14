package io.home.registry.repository;

import io.home.registry.DatabaseManager;
import io.home.registry.model.DeviceConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ConnectionRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(ConnectionRepository.class);
    
    private final DatabaseManager dbManager;
    
    public ConnectionRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }
    
    public DeviceConnection createConnection(DeviceConnection connection) {
        String sql = "INSERT INTO device_connections (device_id, connected_at, ip_address, client_info) VALUES (?, ?, ?, ?)";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setLong(1, connection.getDeviceId());
            stmt.setTimestamp(2, Timestamp.valueOf(connection.getConnectedAt()));
            stmt.setString(3, connection.getIpAddress());
            stmt.setString(4, connection.getClientInfo());
            
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating connection failed, no rows affected.");
            }
            
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    connection.setId(generatedKeys.getLong(1));
                } else {
                    throw new SQLException("Creating connection failed, no ID obtained.");
                }
            }
            
            logger.info("Connection created with ID: {}", connection.getId());
            return connection;
            
        } catch (SQLException e) {
            logger.error("Failed to create connection", e);
            throw new RuntimeException("Database operation failed", e);
        }
    }
    
    public void closeConnection(Long connectionId) {
        String sql = "UPDATE device_connections SET disconnected_at = ? WHERE id = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setLong(2, connectionId);
            
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                logger.warn("No connection found with ID: {}", connectionId);
            } else {
                logger.info("Connection {} closed", connectionId);
            }
            
        } catch (SQLException e) {
            logger.error("Failed to close connection", e);
            throw new RuntimeException("Database operation failed", e);
        }
    }
    
    public Optional<DeviceConnection> findActiveByDeviceId(Long deviceId) {
        String sql = "SELECT * FROM device_connections WHERE device_id = ? AND disconnected_at IS NULL ORDER BY connected_at DESC LIMIT 1";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, deviceId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToConnection(rs));
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find active connection by device ID", e);
            throw new RuntimeException("Database operation failed", e);
        }
        
        return Optional.empty();
    }
    
    public List<DeviceConnection> findActiveConnections() {
        String sql = "SELECT * FROM device_connections WHERE disconnected_at IS NULL ORDER BY connected_at DESC";
        List<DeviceConnection> connections = new ArrayList<>();
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                connections.add(mapResultSetToConnection(rs));
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find active connections", e);
            throw new RuntimeException("Database operation failed", e);
        }
        
        return connections;
    }
    
    public void closeAllConnectionsForDevice(Long deviceId) {
        String sql = "UPDATE device_connections SET disconnected_at = ? WHERE device_id = ? AND disconnected_at IS NULL";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setLong(2, deviceId);
            
            int affectedRows = stmt.executeUpdate();
            logger.info("Closed {} connections for device {}", affectedRows, deviceId);
            
        } catch (SQLException e) {
            logger.error("Failed to close all connections for device", e);
            throw new RuntimeException("Database operation failed", e);
        }
    }
    
    public List<DeviceConnection> findAllByDeviceId(Long deviceId) {
        String sql = "SELECT * FROM device_connections WHERE device_id = ? ORDER BY connected_at DESC";
        List<DeviceConnection> connections = new ArrayList<>();
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, deviceId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    connections.add(mapResultSetToConnection(rs));
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find connections by device ID", e);
            throw new RuntimeException("Database operation failed", e);
        }
        
        return connections;
    }
    
    private DeviceConnection mapResultSetToConnection(ResultSet rs) throws SQLException {
        DeviceConnection connection = new DeviceConnection();
        connection.setId(rs.getLong("id"));
        connection.setDeviceId(rs.getLong("device_id"));
        
        Timestamp connectedAt = rs.getTimestamp("connected_at");
        if (connectedAt != null) {
            connection.setConnectedAt(connectedAt.toLocalDateTime());
        }
        
        Timestamp disconnectedAt = rs.getTimestamp("disconnected_at");
        if (disconnectedAt != null) {
            connection.setDisconnectedAt(disconnectedAt.toLocalDateTime());
        }
        
        connection.setIpAddress(rs.getString("ip_address"));
        connection.setClientInfo(rs.getString("client_info"));
        
        return connection;
    }
}

