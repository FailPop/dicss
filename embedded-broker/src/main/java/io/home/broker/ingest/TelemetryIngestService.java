package io.home.broker.ingest;

import io.home.registry.DatabaseManager;
import io.home.registry.model.Device;
import io.home.registry.model.Telemetry;
import io.home.registry.repository.DeviceRepository;
import io.home.registry.repository.TelemetryRepository;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;

public class TelemetryIngestService {

    private static final Logger logger = LoggerFactory.getLogger(TelemetryIngestService.class);

    private final DeviceRepository deviceRepository;
    private final TelemetryRepository telemetryRepository;

    public TelemetryIngestService(DatabaseManager dbManager) {
        this.deviceRepository = new DeviceRepository(dbManager);
        this.telemetryRepository = new TelemetryRepository(dbManager);
    }

    public void ingest(String topic, byte[] payloadBytes) {
        try {
            if (payloadBytes == null || payloadBytes.length == 0) {
                logger.warn("Skip empty telemetry payload for topic={}", topic);
                return;
            }
            if (payloadBytes.length > 512 * 1024) { // 512KB guard
                logger.warn("Skip oversized telemetry payload ({} bytes) topic={}", payloadBytes.length, topic);
                return;
            }

            String payload = new String(payloadBytes, StandardCharsets.UTF_8);
            ParsedTopic pt = parseTelemetryTopic(topic);
            if (pt == null) {
                logger.debug("Telemetry topic not matched, ignoring: {}", topic);
                return;
            }

            // Find device by serial hash or by serial (depending on registration flow)
            String serial = pt.deviceId; // treat deviceId as serial string for now
            String serialHash = io.home.registry.DeviceIdentityHasher.hash(serial);
            Optional<Device> deviceOpt = deviceRepository.findBySerialHash(serialHash);
            if (deviceOpt.isEmpty()) {
                logger.warn("Unknown device for telemetry: serial={}", serial);
                return;
            }

            Device device = deviceOpt.get();

            // Try parse minimal fields
            LocalDateTime ts = null;
            String measurement = null;
            Double value = null;
            try {
                JSONObject json = new JSONObject(payload);
                if (json.has("timestamp")) {
                    String timestampStr = json.getString("timestamp");
                    try {
                        // Try ISO_LOCAL_DATE_TIME format first
                        ts = LocalDateTime.parse(timestampStr, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    } catch (java.time.format.DateTimeParseException e) {
                        // Try ISO_DATE_TIME (with timezone)
                        try {
                            ts = java.time.Instant.parse(timestampStr).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
                        } catch (Exception e2) {
                            logger.debug("Could not parse timestamp: {}", timestampStr);
                        }
                    }
                }
                if (json.has("measurement")) {
                    measurement = json.getString("measurement");
                }
                if (json.has("value")) {
                    value = json.optDouble("value");
                }
            } catch (Exception e) {
                // keep raw payload if parsing fails
                logger.debug("Telemetry JSON parsing failed, storing raw: {}", e.getMessage());
            }

            Telemetry telemetry = new Telemetry(
                device.getId(),
                topic,
                ts,
                measurement,
                value,
                payload
            );
            telemetry.setReceivedAt(LocalDateTime.now());

            telemetryRepository.insert(telemetry);
            logger.debug("Telemetry stored deviceId={} topic={}", device.getId(), topic);
        } catch (Exception e) {
            logger.error("Telemetry ingest failed for topic={}", topic, e);
        }
    }

    private ParsedTopic parseTelemetryTopic(String topic) {
        // Expected: home/<controllerId>/devices/<deviceId>/telemetry
        String[] parts = topic.split("/");
        if (parts.length >= 5 && "home".equals(parts[0]) && "devices".equals(parts[2]) && "telemetry".equals(parts[4])) {
            String controllerId = parts[1];
            String deviceId = parts[3];
            return new ParsedTopic(controllerId, deviceId);
        }
        return null;
    }

    private static class ParsedTopic {
        @SuppressWarnings("unused")
        final String controllerId;
        final String deviceId;

        ParsedTopic(String controllerId, String deviceId) {
            this.controllerId = controllerId;
            this.deviceId = deviceId;
        }
    }
}


