package io.home.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class DeviceIdentityHasher {
    
    private static final Logger logger = LoggerFactory.getLogger(DeviceIdentityHasher.class);
    
    public static String hashSerial(String serial) {
        return hash(serial);
    }
    
    public static String hashMac(String mac) {
        return hash(mac);
    }
    
    public static String hashComposite(String serial, String mac) {
        String combined = serial + "|" + mac;
        return hash(combined);
    }
    
    public static String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes());
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            logger.error("SHA-256 algorithm not available", e);
            throw new RuntimeException("Hashing failed", e);
        }
    }
}
