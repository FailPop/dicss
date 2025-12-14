package io.home.registry.repository;

import io.home.registry.DatabaseManager;
import io.home.registry.model.ClientBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Optional;

public class ClientBindingRepository {

    private static final Logger logger = LoggerFactory.getLogger(ClientBindingRepository.class);

    private final DatabaseManager dbManager;

    public ClientBindingRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public ClientBinding insert(ClientBinding binding) {
        String sql = "INSERT INTO client_bindings (uuid, fingerprint, role) VALUES (?,?,?)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, binding.getUuid());
            ps.setString(2, binding.getFingerprint());
            ps.setString(3, binding.getRole());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) binding.setId(rs.getLong(1));
            }
            logger.info("Client binding created uuid={}", binding.getUuid());
            return binding;
        } catch (SQLException e) {
            logger.error("Failed to insert client binding", e);
            throw new RuntimeException(e);
        }
    }

    public Optional<ClientBinding> findByUuid(String uuid) {
        String sql = "SELECT * FROM client_bindings WHERE uuid=?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ClientBinding b = new ClientBinding(
                        rs.getString("uuid"),
                        rs.getString("fingerprint"),
                        rs.getString("role")
                    );
                    b.setId(rs.getLong("id"));
                    Timestamp ls = rs.getTimestamp("last_seen_at");
                    if (ls != null) b.setLastSeenAt(ls.toLocalDateTime());
                    Timestamp cr = rs.getTimestamp("created_at");
                    if (cr != null) b.setCreatedAt(cr.toLocalDateTime());
                    return Optional.of(b);
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}


