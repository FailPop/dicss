package io.home.backend.config;

import io.home.registry.DatabaseManager;
import io.home.registry.service.AdminService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdminServiceProducer {
    
    private static final Logger logger = LoggerFactory.getLogger(AdminServiceProducer.class);
    
    @Produces
    @ApplicationScoped
    public AdminService createAdminService() {
        logger.info("Creating AdminService instance");
        DatabaseManager dbManager = new DatabaseManager();
        return new AdminService(dbManager);
    }
}

