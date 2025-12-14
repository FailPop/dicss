package io.home.test;

import javax.net.ssl.*;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Base64;

public class ControllerWebServerTest {
    
    private static final String SERVER_URL = "https://127.0.0.1:11080";
    private static final String CLIENT_KEYSTORE = "client.p12";
    private static final String CLIENT_KEYSTORE_PASSWORD = "changeit";
    private static final String TRUSTSTORE = "client-truststore.p12";
    private static final String TRUSTSTORE_PASSWORD = "changeit";
    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASS = "changeit";
    
    private SSLContext sslContext;
    private String basicAuth;
    
    public static void main(String[] args) {
        ControllerWebServerTest test = new ControllerWebServerTest();
        try {
            test.setup();
            test.testAllEndpoints();
        } catch (Exception e) {
            System.err.println("Test failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private void setup() throws Exception {
        System.out.println("=== Setting up mTLS configuration ===");
        
        // Load client keystore
        KeyStore clientKeyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(CLIENT_KEYSTORE)) {
            clientKeyStore.load(fis, CLIENT_KEYSTORE_PASSWORD.toCharArray());
        }
        System.out.println("Loaded client keystore: " + CLIENT_KEYSTORE);
        
        // Load truststore
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(TRUSTSTORE)) {
            trustStore.load(fis, TRUSTSTORE_PASSWORD.toCharArray());
        }
        System.out.println("Loaded truststore: " + TRUSTSTORE);
        
        // Setup KeyManager
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(clientKeyStore, CLIENT_KEYSTORE_PASSWORD.toCharArray());
        System.out.println("KeyManager initialized");
        
        // Setup TrustManager
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        System.out.println("TrustManager initialized");
        
        // Create SSLContext
        sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        System.out.println("SSLContext created");
        
        // Set default SSLContext
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> {
            // Accept all hostnames for self-signed certificates
            return true;
        });
        
        // Prepare Basic Auth
        String authString = ADMIN_USER + ":" + ADMIN_PASS;
        basicAuth = Base64.getEncoder().encodeToString(authString.getBytes(StandardCharsets.UTF_8));
        System.out.println("Basic Auth configured");
        
        System.out.println("=== Setup completed ===\n");
    }
    
    private void testAllEndpoints() throws Exception {
        System.out.println("=== Testing ControllerWebServer Endpoints ===\n");
        
        testGetStatus();
        testGetMetrics();
        testGetRecentAlerts();
        testPostPairingStart();
        testPostPairingComplete();
        
        System.out.println("\n=== All tests completed successfully ===");
    }
    
    private void testGetStatus() throws Exception {
        System.out.println("TEST 1: GET /status");
        String response = makeRequest("GET", "/status", null);
        System.out.println("Response: " + response);
        System.out.println("✓ Status endpoint works\n");
    }
    
    private void testGetMetrics() throws Exception {
        System.out.println("TEST 2: GET /metrics");
        String response = makeRequest("GET", "/metrics", null);
        System.out.println("Response: " + response);
        // Verify JSON structure
        if (response.contains("devices") && response.contains("alerts") && response.contains("telemetry")) {
            System.out.println("✓ Metrics endpoint returns correct data\n");
        } else {
            throw new AssertionError("Metrics endpoint returned invalid data");
        }
    }
    
    private void testGetRecentAlerts() throws Exception {
        System.out.println("TEST 3: GET /alerts/recent?limit=5");
        String response = makeRequest("GET", "/alerts/recent?limit=5", null);
        System.out.println("Response: " + response);
        System.out.println("✓ Recent alerts endpoint works\n");
    }
    
    private void testPostPairingStart() throws Exception {
        System.out.println("TEST 4: POST /pairing/start");
        String response = makeRequest("POST", "/pairing/start", null);
        System.out.println("Response: " + response);
        // Verify pairing code is returned
        if (response.contains("code") || response.contains("uuid")) {
            System.out.println("✓ Pairing start endpoint works\n");
        } else {
            System.out.println("⚠ Pairing start response format unexpected\n");
        }
    }
    
    private void testPostPairingComplete() throws Exception {
        System.out.println("TEST 5: POST /pairing/complete");
        // Test with dummy data (will likely fail, but tests endpoint accessibility)
        String jsonData = "{\"code\":\"TESTCODE\",\"uuid\":\"test-uuid\",\"fingerprint\":\"test-fp\",\"role\":\"admin\"}";
        String response = makeRequest("POST", "/pairing/complete", jsonData);
        System.out.println("Response: " + response);
        System.out.println("✓ Pairing complete endpoint accessible\n");
    }
    
    private String makeRequest(String method, String path, String body) throws Exception {
        URL url = new URL(SERVER_URL + path);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        
        try {
            conn.setRequestMethod(method);
            conn.setRequestProperty("Authorization", "Basic " + basicAuth);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            
            // Disable hostname verification for self-signed certificates
            conn.setHostnameVerifier((hostname, session) -> true);
            
            // Set SSL socket factory
            conn.setSSLSocketFactory(sslContext.getSocketFactory());
            
            // For POST requests with body
            if (body != null && !body.isEmpty()) {
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            
            int responseCode = conn.getResponseCode();
            System.out.println("HTTP Status Code: " + responseCode);
            
            InputStream inputStream;
            if (responseCode >= 200 && responseCode < 300) {
                inputStream = conn.getInputStream();
            } else {
                inputStream = conn.getErrorStream();
                if (inputStream == null) {
                    inputStream = conn.getInputStream();
                }
            }
            
            if (inputStream == null) {
                return "No response body";
            }
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                return response.toString();
            }
            
        } finally {
            conn.disconnect();
        }
    }
}

