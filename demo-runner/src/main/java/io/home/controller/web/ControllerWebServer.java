package io.home.controller.web;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsServer;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.home.registry.DatabaseManager;
import io.home.registry.model.ClientBinding;
import io.home.registry.model.AuditLog;
import io.home.registry.repository.ClientBindingRepository;
import io.home.registry.repository.AuditLogRepository;

public final class ControllerWebServer implements AutoCloseable {
	private static final Logger logger = LoggerFactory.getLogger(ControllerWebServer.class);

	private final HttpServer server;
	private final String adminUser;
	private final String adminPass;
	private final ClientBindingRepository bindingRepository;
	private final AuditLogRepository auditRepository;
	private final Map<String, Long> pairingCodes = new ConcurrentHashMap<>();

	public static class Config {
		public int port = Integer.parseInt(System.getProperty("controller.web.port", "11080"));
		public boolean allowRemote = Boolean.parseBoolean(System.getProperty("controller.web.remote.enabled", "false"));
		public String adminUser = System.getProperty("controller.web.admin.user", "admin");
		public String adminPass = System.getProperty("controller.web.admin.pass", "changeit");
	}

    public ControllerWebServer(Config cfg) throws IOException {
        InetAddress bind = cfg.allowRemote ? InetAddress.getByName("0.0.0.0") : InetAddress.getByName("127.0.0.1");
        boolean tlsEnabled = Boolean.parseBoolean(System.getProperty("controller.web.tls.enabled", "true"));
        if (tlsEnabled) {
            this.server = createHttpsServer(bind, cfg.port);
        } else {
            this.server = HttpServer.create(new InetSocketAddress(bind, cfg.port), 0);
        }
		this.adminUser = cfg.adminUser;
		this.adminPass = cfg.adminPass;

		DatabaseManager db = new DatabaseManager();
		this.bindingRepository = new ClientBindingRepository(db);
		this.auditRepository = new AuditLogRepository(db);

		server.createContext("/status", this::handleStatus);
		server.createContext("/settings/remote", this::handleRemoteInfo);
		server.createContext("/pairing/start", this::handlePairingStart);
		server.createContext("/pairing/complete", this::handlePairingComplete);
		server.createContext("/metrics", this::handleMetrics);
		server.createContext("/alerts/recent", this::handleRecentAlerts);
		server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
	}

	public void start() {
		server.start();
		logger.info("ControllerWebServer started on {}:{}. Remote access: {}",
			server.getAddress().getAddress().getHostAddress(), server.getAddress().getPort(),
			!server.getAddress().getAddress().isLoopbackAddress());
	}

	private void handleStatus(HttpExchange exchange) throws IOException {
		if (!requireAuth(exchange)) return;
		respondJson(exchange, 200, "{\"status\":\"ok\",\"mode\":\"local-only by default\"}");
	}

	private void handleRemoteInfo(HttpExchange exchange) throws IOException {
		if (!requireAuth(exchange)) return;
		boolean isRemote = !server.getAddress().getAddress().isLoopbackAddress();
		String body = "{\"remoteEnabled\": " + isRemote + ", \"hint\": \"toggle via -Dcontroller.web.remote.enabled=true\"}";
		respondJson(exchange, 200, body);
	}

	private void handleMetrics(HttpExchange exchange) throws IOException {
		if (!requireAuth(exchange)) return;
		try {
			io.home.registry.DatabaseManager db = new io.home.registry.DatabaseManager();
			long devices = count(db, "SELECT COUNT(*) FROM devices");
			long alerts = count(db, "SELECT COUNT(*) FROM security_alerts");
			long telemetry = count(db, "SELECT COUNT(*) FROM telemetry");
			String body = "{\"devices\":" + devices + ",\"alerts\":" + alerts + ",\"telemetry\":" + telemetry + "}";
			respondJson(exchange, 200, body);
		} catch (Exception e) {
			respondJson(exchange, 500, "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
		}
	}

	private long count(io.home.registry.DatabaseManager db, String sql) throws java.sql.SQLException {
		try (java.sql.Connection c = db.getConnection();
			 java.sql.PreparedStatement ps = c.prepareStatement(sql);
			 java.sql.ResultSet rs = ps.executeQuery()) {
			if (rs.next()) return rs.getLong(1);
			return 0L;
		}
	}

	private void handleRecentAlerts(HttpExchange exchange) throws IOException {
		if (!requireAuth(exchange)) return;
		int limit = 10;
		String q = exchange.getRequestURI().getQuery();
		if (q != null && q.contains("limit=")) {
			try { limit = Integer.parseInt(q.replaceAll(".*limit=([0-9]+).*", "$1")); } catch (Exception ignored) {}
		}
		try {
			io.home.registry.DatabaseManager db = new io.home.registry.DatabaseManager();
			StringBuilder sb = new StringBuilder();
			sb.append("[");
			try (java.sql.Connection c = db.getConnection();
				 java.sql.PreparedStatement ps = c.prepareStatement("SELECT alert_type, device_serial_hash, details, created_at FROM security_alerts ORDER BY id DESC LIMIT ?")) {
				ps.setInt(1, limit);
				try (java.sql.ResultSet rs = ps.executeQuery()) {
					boolean first = true;
					while (rs.next()) {
						if (!first) sb.append(",");
						first = false;
						sb.append("{\"type\":\"").append(rs.getString(1)).append("\",")
							.append("\"device\":\"").append(rs.getString(2)).append("\",")
							.append("\"details\":").append(rs.getString(3) == null ? "null" : rs.getString(3))
							.append(",\"created_at\":\"").append(String.valueOf(rs.getTimestamp(4))).append("\"}");
					}
				}
			}
			sb.append("]");
			respondJson(exchange, 200, sb.toString());
		} catch (Exception e) {
			respondJson(exchange, 500, "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
		}
	}

	// Admin: issue pairing code valid for 5 minutes
	private void handlePairingStart(HttpExchange exchange) throws IOException {
		if (!requireAuth(exchange)) return;
		String code = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
		pairingCodes.put(code, System.currentTimeMillis() + 5 * 60_000);
		auditRepository.insert(new AuditLog("PAIRING_CODE_ISSUED", adminUser, "{\"code\":\"" + code + "\"}"));
		respondJson(exchange, 200, "{\"code\":\"" + code + "\",\"ttlSeconds\":300}");
	}

	// Client: complete pairing with code, uuid, fingerprint, role
	private void handlePairingComplete(HttpExchange exchange) throws IOException {
		if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
			respondJson(exchange, 405, "{\"error\":\"method not allowed\"}");
			return;
		}
		String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		String code = jsonGet(body, "code");
		String uuid = jsonGet(body, "uuid");
		String fingerprint = jsonGet(body, "fingerprint");
		String role = jsonGetOr(body, "role", "user");
		if (code == null || uuid == null || fingerprint == null) {
			respondJson(exchange, 400, "{\"error\":\"code, uuid, fingerprint required\"}");
			return;
		}
		Long exp = pairingCodes.get(code);
		if (exp == null || exp < System.currentTimeMillis()) {
			respondJson(exchange, 400, "{\"error\":\"code invalid or expired\"}");
			return;
		}
		try {
			bindingRepository.insert(new ClientBinding(uuid, fingerprint, role));
			auditRepository.insert(new AuditLog("CLIENT_PAIRED", uuid, "{\"fingerprint\":\"" + fingerprint + "\",\"role\":\"" + role + "\"}"));
			pairingCodes.remove(code);
			respondJson(exchange, 200, "{\"status\":\"paired\"}");
		} catch (Exception e) {
			respondJson(exchange, 500, "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
		}
	}

	private boolean requireAuth(HttpExchange exchange) throws IOException {
		String auth = exchange.getRequestHeaders().getFirst("Authorization");
		if (auth == null || !auth.startsWith("Basic ")) {
			unauthorized(exchange);
			return false;
		}
		String b64 = auth.substring("Basic ".length());
		String decoded = new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
		int idx = decoded.indexOf(':');
		if (idx < 0) {
			unauthorized(exchange);
			return false;
		}
		String u = decoded.substring(0, idx);
		String p = decoded.substring(idx + 1);
		if (!adminUser.equals(u) || !adminPass.equals(p)) {
			unauthorized(exchange);
			return false;
		}
		return true;
	}

	private void unauthorized(HttpExchange exchange) throws IOException {
		exchange.getResponseHeaders().add("WWW-Authenticate", "Basic realm=controller");
		respondJson(exchange, 401, "{\"error\":\"unauthorized\"}");
	}

	private void respondJson(HttpExchange exchange, int code, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
		exchange.sendResponseHeaders(code, bytes.length);
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(bytes);
		}
	}

	private String jsonGet(String json, String key) {
		String k = '"' + key + '"';
		int i = json.indexOf(k);
		if (i < 0) return null;
		int c = json.indexOf(':', i + k.length());
		if (c < 0) return null;
		int q1 = json.indexOf('"', c + 1);
		if (q1 < 0) return null;
		int q2 = json.indexOf('"', q1 + 1);
		if (q2 < 0) return null;
		return json.substring(q1 + 1, q2);
	}

	private String jsonGetOr(String json, String key, String def) {
		String v = jsonGet(json, key);
		return v != null ? v : def;
	}

	@Override
	public void close() {
		server.stop(0);
		logger.info("ControllerWebServer stopped");
	}

    private HttpServer createHttpsServer(InetAddress bind, int port) {
        try {
            String ksPath = System.getProperty("controller.web.keystore.path", System.getProperty("server.keystore", "server-keystore.p12"));
            String ksPass = System.getProperty("controller.web.keystore.password", System.getProperty("server.keystore.password", "changeit"));
            String tsPath = System.getProperty("controller.web.truststore.path", System.getProperty("broker.truststore", "broker-truststore.p12"));
            String tsPass = System.getProperty("controller.web.truststore.password", System.getProperty("broker.truststore.password", "changeit"));

            java.security.KeyStore keyStore = java.security.KeyStore.getInstance("PKCS12");
            try (java.io.FileInputStream fis = new java.io.FileInputStream(ksPath)) {
                keyStore.load(fis, ksPass.toCharArray());
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, ksPass.toCharArray());

            java.security.KeyStore trustStore = java.security.KeyStore.getInstance("PKCS12");
            try (java.io.FileInputStream fis = new java.io.FileInputStream(tsPath)) {
                trustStore.load(fis, tsPass.toCharArray());
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

            HttpsServer httpsServer = HttpsServer.create(new InetSocketAddress(bind, port), 0);
            javax.net.ssl.SSLParameters params = sslContext.getDefaultSSLParameters();
            params.setNeedClientAuth(true);
            httpsServer.setHttpsConfigurator(new com.sun.net.httpserver.HttpsConfigurator(sslContext) {
                @Override
                public void configure(com.sun.net.httpserver.HttpsParameters p) {
                    p.setSSLParameters(params);
                }
            });
            return httpsServer;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create HTTPS server", e);
        }
    }
}
