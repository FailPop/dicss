package io.home.controller.core;

import io.home.broker.EmbeddedMoquetteBroker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public final class BrokerService {

    private static final Logger logger = LoggerFactory.getLogger(BrokerService.class);
    private static volatile BrokerService instance;

    private final Object lifecycleLock = new Object();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private EmbeddedMoquetteBroker broker;

    private BrokerService() {
        // hidden
    }

    public static BrokerService getInstance() {
        BrokerService local = instance;
        if (local == null) {
            synchronized (BrokerService.class) {
                local = instance;
                if (local == null) {
                    local = new BrokerService();
                    instance = local;
                }
            }
        }
        return local;
    }

    public void start(String serverKeystorePath,
                      String serverKeystorePassword,
                      String brokerTruststorePath,
                      String brokerTruststorePassword) throws Exception {
        if (started.get()) {
            logger.info("BrokerService already started");
            return;
        }
        synchronized (lifecycleLock) {
            if (started.get()) {
                return;
            }
            broker = new EmbeddedMoquetteBroker(
                serverKeystorePath,
                serverKeystorePassword,
                brokerTruststorePath,
                brokerTruststorePassword
            );
            broker.start();
            started.set(true);
            logger.info("BrokerService started");
        }
    }

    public void stop() {
        synchronized (lifecycleLock) {
            if (!started.get()) {
                return;
            }
            try {
                if (broker != null) {
                    broker.close();
                }
            } catch (Exception e) {
                logger.error("Error while stopping broker", e);
            } finally {
                broker = null;
                started.set(false);
                logger.info("BrokerService stopped");
            }
        }
    }

    public boolean isStarted() {
        return started.get();
    }
}


