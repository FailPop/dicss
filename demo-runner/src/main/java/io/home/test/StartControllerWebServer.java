package io.home.test;

import io.home.controller.web.ControllerWebServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StartControllerWebServer {
    private static final Logger logger = LoggerFactory.getLogger(StartControllerWebServer.class);
    
    public static void main(String[] args) {
        try {
            ControllerWebServer.Config cfg = new ControllerWebServer.Config();
            ControllerWebServer server = new ControllerWebServer(cfg);
            server.start();
            
            logger.info("ControllerWebServer started on port {}. Press Ctrl+C to stop.", cfg.port);
            
            // Keep running
            Thread.sleep(Long.MAX_VALUE);
        } catch (Exception e) {
            logger.error("Failed to start ControllerWebServer", e);
            System.exit(1);
        }
    }
}

