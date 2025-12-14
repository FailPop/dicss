package io.home.broker.service;

import io.home.registry.DeviceIdentityHasher;
import io.home.registry.DatabaseManager;
import io.home.registry.model.Device;
import io.home.registry.model.Telemetry;
import io.home.registry.repository.DeviceRepository;
import io.home.registry.repository.TelemetryRepository;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Optional;

public class TelemetryIngestService {

    private static final Logger logger = LoggerFactory.getLogger(TelemetryIngestService.class);
    private static final int MAX_PAYLOAD_BYTES = 64 * 1024; // 64KB safety limit

    private final DeviceRepository deviceRepository;
    private final TelemetryRepository telemetryRepository;

    public TelemetryIngestService(DatabaseManager dbManager) {
        this.deviceRepository = new DeviceRepository(dbManager);
        this.telemetryRepository = new TelemetryRepository(dbManager);
    }

    public void ingest(String topic, byte[] payload) {
        try {
            if (payload == null || payload.length == 0) {
                logger.warn("Empty telemetry payload for topic: {}", topic);
                return;
            }
            if (payload.length > MAX_PAYLOAD_BYTES) {
                logger.warn("Telemetry payload exceeded limit: {} bytes on topic {}", payload.length, topic);
                return;
            }

            // Expect topic: home/<controllerId>/devices/<deviceId>/telemetry
            String[] parts = topic.split("/");
            if (parts.length < 5 || !"home".equals(parts[0]) || !"devices".equals(parts[2]) || !"telemetry".equals(parts[4])) {
                logger.debug("Skipping non-telemetry topic: {}", topic);
                return;
            }
            String deviceId = parts[3];

            String payloadStr = new String(payload, java.nio.charset.StandardCharsets.UTF_8);
            JSONObject json;
            try {
                json = new JSONObject(payloadStr);
            } catch (Exception e) {
                logger.warn("Invalid JSON telemetry from device {}: {}", deviceId, e.getMessage());
                return;
            }

            // Optional normalized fields
            LocalDateTime ts = null;
            if (json.has("timestamp")) {
                try {
                    ts = LocalDateTime.parse(json.getString("timestamp"));
                } catch (Exception ignored) { }
            }
            String measurement = json.optString("measurement", null);
            Double value = json.has("value") ? json.optDouble("value") : null;

            // Resolve device by composite hash (if serial/mac provided) else by serial hash as fallback
            Optional<Device> deviceOpt = Optional.empty();
            String serial = json.optString("serial", null);
            String mac = json.optString("mac", null);
            if (serial != null && mac != null) {
                String composite = DeviceIdentityHasher.hashComposite(serial, mac);
                deviceOpt = deviceRepository.findByCompositeHash(composite);
            }
            if (deviceOpt.isEmpty() && serial != null) {
                // Fallback by serial hash
                String serialHash = DeviceIdentityHasher.hash(serial);
                deviceOpt = deviceRepository.findBySerialHash(serialHash);
            }

            if (deviceOpt.isEmpty()) {
                logger.warn("Device not found for telemetry: topic={} json.serial={} json.mac={}", topic, serial, mac);
                return;
            }

            Device device = deviceOpt.get();
            Telemetry telemetry = new Telemetry(device.getId(), topic, ts, measurement, value, payloadStr);
            telemetryRepository.insert(telemetry);
            logger.debug("Telemetry stored: deviceId={}, topic={}", device.getId(), topic);

        } catch (Exception e) {
            logger.error("Failed to ingest telemetry from topic {}", topic, e);
        }
    }
}


