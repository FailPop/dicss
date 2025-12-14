package io.home.backend.config;

import io.home.registry.DatabaseManager;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseManagerProducer {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManagerProducer.class);
    
    @Produces
    @ApplicationScoped
    public DatabaseManager createDatabaseManager() {
        logger.info("Creating DatabaseManager instance");
        return new DatabaseManager();
    }
}

