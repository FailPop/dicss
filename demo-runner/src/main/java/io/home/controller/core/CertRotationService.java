package io.home.controller.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class CertRotationService implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(CertRotationService.class);

    private final ScheduledExecutorService scheduler;
    private final Random random = new Random();

    private final String serverKeystorePath;
    private final String serverTruststorePath;
    private final String clientKeystorePath;
    private final String clientTruststorePath;

    private volatile long lastServerKsMtime;
    private volatile long lastServerTsMtime;
    private volatile long lastClientKsMtime;
    private volatile long lastClientTsMtime;

    private final long minIntervalSeconds;
    private final long maxIntervalSeconds;

    public CertRotationService(String serverKeystorePath, String serverTruststorePath,
                               String clientKeystorePath, String clientTruststorePath) {
        this.serverKeystorePath = serverKeystorePath;
        this.serverTruststorePath = serverTruststorePath;
        this.clientKeystorePath = clientKeystorePath;
        this.clientTruststorePath = clientTruststorePath;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cert-rotation");
            t.setDaemon(true);
            return t;
        });

        // Defaults: random in [7d, 30d]
        this.minIntervalSeconds = Long.parseLong(System.getProperty("cert.rotation.min.seconds", String.valueOf(Duration.ofDays(7).getSeconds())));
        this.maxIntervalSeconds = Long.parseLong(System.getProperty("cert.rotation.max.seconds", String.valueOf(Duration.ofDays(30).getSeconds())));
    }

    public void start() {
        snapshotMtims();
        long delay = computeNextDelaySeconds();
        logger.info("CertRotationService scheduled in {} hours", delay / 3600);
        scheduler.schedule(this::tick, delay, TimeUnit.SECONDS);
        // also start file watcher tick every 5 minutes
        scheduler.scheduleAtFixedRate(this::checkFilesChanged, 5, 5, TimeUnit.MINUTES);
    }

    private void tick() {
        try {
            logger.info("CertRotationService tick at {}", Instant.now());
            // On tick we just attempt broker reload (assumes external process rotated files or we keep same files)
            restartBroker();
        } catch (Exception e) {
            logger.error("Cert rotation tick failed", e);
        } finally {
            long delay = computeNextDelaySeconds();
            logger.info("Next cert rotation in {} hours", delay / 3600);
            scheduler.schedule(this::tick, delay, TimeUnit.SECONDS);
        }
    }

    private void checkFilesChanged() {
        try {
            File sk = new File(serverKeystorePath);
            File st = new File(serverTruststorePath);
            File ck = new File(clientKeystorePath);
            File ct = new File(clientTruststorePath);
            boolean changed = false;
            if (sk.exists() && sk.lastModified() != lastServerKsMtime) { changed = true; lastServerKsMtime = sk.lastModified(); }
            if (st.exists() && st.lastModified() != lastServerTsMtime) { changed = true; lastServerTsMtime = st.lastModified(); }
            if (ck.exists() && ck.lastModified() != lastClientKsMtime) { changed = true; lastClientKsMtime = ck.lastModified(); }
            if (ct.exists() && ct.lastModified() != lastClientTsMtime) { changed = true; lastClientTsMtime = ct.lastModified(); }
            if (changed) {
                logger.info("Detected keystore/truststore change on disk, reloading broker");
                restartBroker();
            }
        } catch (Exception e) {
            logger.error("File change check failed", e);
        }
    }

    private void restartBroker() {
        BrokerService broker = BrokerService.getInstance();
        if (broker.isStarted()) {
            broker.stop();
        }
        try {
            broker.start(serverKeystorePath,
                    System.getProperty("server.keystore.password", "changeit"),
                    serverTruststorePath,
                    System.getProperty("broker.truststore.password", "changeit"));
        } catch (Exception e) {
            logger.error("Failed to restart broker with new certificates", e);
        }
    }

    private void snapshotMtims() {
        File sk = new File(serverKeystorePath);
        File st = new File(serverTruststorePath);
        File ck = new File(clientKeystorePath);
        File ct = new File(clientTruststorePath);
        this.lastServerKsMtime = sk.exists() ? sk.lastModified() : 0L;
        this.lastServerTsMtime = st.exists() ? st.lastModified() : 0L;
        this.lastClientKsMtime = ck.exists() ? ck.lastModified() : 0L;
        this.lastClientTsMtime = ct.exists() ? ct.lastModified() : 0L;
    }

    private long computeNextDelaySeconds() {
        long min = Math.min(minIntervalSeconds, maxIntervalSeconds);
        long max = Math.max(minIntervalSeconds, maxIntervalSeconds);
        long span = max - min;
        long rnd = span > 0 ? (Math.abs(random.nextLong()) % (span + 1)) : 0;
        return min + rnd;
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}


