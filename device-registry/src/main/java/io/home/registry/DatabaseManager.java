package io.home.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    
    // Configuration via system properties with defaults
    private static final String DB_URL = System.getProperty("db.url", "jdbc:postgresql://localhost:5432/mqtt");
    private static final String DB_USER = System.getProperty("db.user", "postgres");
    private static final String DB_PASSWORD = System.getProperty("db.password", "postgres");
    private static final String ENV = System.getProperty("app.environment", "dev");
    
    public DatabaseManager() {
        initializeDatabase();
    }
    
    private void initializeDatabase() {
        try {
            Class.forName("org.postgresql.Driver");
            
            // Test connection and create schema
            try (Connection testConn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                logger.info("Connected to PostgreSQL database: {} (environment: {})", DB_URL, ENV);
                createSchema(testConn);
            }
            
        } catch (Exception e) {
            logger.error("Failed to initialize database", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }
    
    private void createSchema(Connection conn) throws Exception {
        try (InputStream schemaStream = getClass().getClassLoader().getResourceAsStream("schema.sql")) {
            if (schemaStream == null) {
                throw new RuntimeException("Schema file not found");
            }
            
            String schema = new String(schemaStream.readAllBytes(), StandardCharsets.UTF_8);
            // Remove single-line comments (both standalone and at end of lines)
            // Process line by line to preserve structure
            StringBuilder cleanedSchema = new StringBuilder();
            for (String line : schema.split("\n")) {
                int commentIndex = line.indexOf("--");
                if (commentIndex >= 0) {
                    line = line.substring(0, commentIndex);
                }
                if (!line.trim().isEmpty()) {
                    cleanedSchema.append(line).append("\n");
                }
            }
            schema = cleanedSchema.toString();
            // Split by semicolon
            String[] statements = schema.split(";");
            
            try (Statement stmt = conn.createStatement()) {
                int executedCount = 0;
                for (String statement : statements) {
                    String trimmed = statement.trim();
                    // Skip empty statements and comments
                    if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                        continue;
                    }
                    
                    logger.debug("Executing SQL statement {}", executedCount + 1);
                    try {
                        stmt.execute(trimmed);
                        executedCount++;
                    } catch (SQLException e) {
                        String errorMsg = e.getMessage();
                        // Ignore "already exists" errors for IF NOT EXISTS statements
                        boolean isIgnorable = errorMsg.contains("already exists") 
                            || errorMsg.contains("duplicate")
                            || errorMsg.contains("уже существует")
                            || (errorMsg.contains("does not exist") && trimmed.toUpperCase().startsWith("DROP"));
                        
                        if (!isIgnorable) {
                            logger.error("Failed to execute SQL statement: {}", trimmed.substring(0, Math.min(100, trimmed.length())));
                            logger.error("SQL error: {}", errorMsg);
                            // For critical errors, still try to continue but log the error
                            // This allows partial schema creation if some tables already exist
                        } else {
                            logger.debug("Ignoring expected SQL message: {}", errorMsg);
                        }
                    }
                }
                logger.info("Database schema creation completed. Executed {} statements.", executedCount);
            }
            
            logger.info("Database schema created successfully");
        }
    }
    
    /**
     * Get a new database connection for each request.
     * This allows concurrent access from multiple threads.
     * Connections should be closed by the caller using try-with-resources.
     */
    public Connection getConnection() {
        try {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            return conn;
        } catch (SQLException e) {
            logger.error("Failed to get database connection", e);
            throw new RuntimeException("Database connection failed", e);
        }
    }
    
    public void close() {
        // PostgreSQL connections are managed by connection pool or closed by caller
        // No persistent schema connection needed
        logger.debug("DatabaseManager.close() called - no action needed for PostgreSQL");
    }
}

