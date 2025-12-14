package io.home.client;

import java.util.Objects;

public class DeviceInfo {
    private String serial;
    private String mac;
    private String deviceType;
    
    public DeviceInfo() {}
    
    public DeviceInfo(String serial, String mac, String deviceType) {
        this.serial = serial;
        this.mac = mac;
        this.deviceType = deviceType;
        validate();
    }
    
    private void validate() {
        if (serial == null || serial.trim().isEmpty()) {
            throw new IllegalArgumentException("Serial number cannot be null or empty");
        }
        if (mac == null || mac.trim().isEmpty()) {
            throw new IllegalArgumentException("MAC address cannot be null or empty");
        }
        if (deviceType == null || deviceType.trim().isEmpty()) {
            throw new IllegalArgumentException("Device type cannot be null or empty");
        }
        
        // Validate MAC format (basic check)
        if (!mac.matches("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$")) {
            throw new IllegalArgumentException("Invalid MAC address format: " + mac);
        }
        
        // Validate device type
        if (!isValidDeviceType(deviceType)) {
            throw new IllegalArgumentException("Invalid device type: " + deviceType);
        }
    }
    
    private boolean isValidDeviceType(String type) {
        return "TEMP_SENSOR".equals(type) || 
               "SMART_PLUG".equals(type) || 
               "ENERGY_SENSOR".equals(type) || 
               "SMART_SWITCH".equals(type);
    }
    
    public String getSerial() { return serial; }
    public void setSerial(String serial) { 
        this.serial = serial; 
        validate();
    }
    
    public String getMac() { return mac; }
    public void setMac(String mac) { 
        this.mac = mac; 
        validate();
    }
    
    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { 
        this.deviceType = deviceType; 
        validate();
    }
    
    public String generateClientId() {
        return "IOT" + serial.substring(serial.length() - 4) + mac.replace(":", "").substring(0, 6);
    }
    
    public String generateClientId(String suffix) {
        return generateClientId() + suffix;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeviceInfo that = (DeviceInfo) o;
        return Objects.equals(serial, that.serial) && 
               Objects.equals(mac, that.mac) && 
               Objects.equals(deviceType, that.deviceType);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(serial, mac, deviceType);
    }
    
    @Override
    public String toString() {
        return "DeviceInfo{" +
                "serial='" + serial + '\'' +
                ", mac='" + mac + '\'' +
                ", deviceType='" + deviceType + '\'' +
                '}';
    }
}
