package io.home.controller.access.model;

import java.util.Objects;

/**
 * Модель данных карты для записи в блок памяти контроллера.
 * Каждая карта занимает 8 байт:
 * - Байты 0-3: UID карты (4 байта)
 * - Байты 4-6: Разрешенные расписания (битовая маска, 3 байта)
 * - Байт 7: Поведение (0=заблокировано, 1=реле1, 2=оба реле)
 */
public class CardData {
    
    private byte[] uid; // 4 байта
    private byte[] allowedSchedules; // 3 байта (битовая маска)
    private byte behavior; // 1 байт
    
    public CardData() {
        this.uid = new byte[4];
        this.allowedSchedules = new byte[3];
        this.behavior = 0;
    }
    
    public CardData(byte[] uid, byte[] allowedSchedules, byte behavior) {
        if (uid == null || uid.length != 4) {
            throw new IllegalArgumentException("UID must be exactly 4 bytes");
        }
        if (allowedSchedules == null || allowedSchedules.length != 3) {
            throw new IllegalArgumentException("Allowed schedules must be exactly 3 bytes");
        }
        this.uid = uid.clone();
        this.allowedSchedules = allowedSchedules.clone();
        this.behavior = behavior;
    }
    
    /**
     * Создание CardData из hex строки UID.
     * @param uidHex UID в формате hex (например, "01020304")
     * @param allowedSchedules битовая маска расписаний (3 байта)
     * @param behavior поведение (0, 1, или 2)
     */
    public CardData(String uidHex, byte[] allowedSchedules, byte behavior) {
        this(hexStringToBytes(uidHex, 4), allowedSchedules, behavior);
    }
    
    /**
     * Преобразование карты в массив байт (8 байт).
     */
    public byte[] toByteArray() {
        byte[] result = new byte[8];
        System.arraycopy(uid, 0, result, 0, 4);
        System.arraycopy(allowedSchedules, 0, result, 4, 3);
        result[7] = behavior;
        return result;
    }
    
    /**
     * Преобразование hex строки в массив байт.
     */
    private static byte[] hexStringToBytes(String hex, int expectedLength) {
        if (hex == null || hex.length() != expectedLength * 2) {
            throw new IllegalArgumentException("Hex string must be " + (expectedLength * 2) + " characters");
        }
        byte[] bytes = new byte[expectedLength];
        for (int i = 0; i < expectedLength; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
    
    public byte[] getUid() {
        return uid.clone();
    }
    
    public void setUid(byte[] uid) {
        if (uid == null || uid.length != 4) {
            throw new IllegalArgumentException("UID must be exactly 4 bytes");
        }
        this.uid = uid.clone();
    }
    
    public String getUidHex() {
        StringBuilder sb = new StringBuilder();
        for (byte b : uid) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }
    
    public byte[] getAllowedSchedules() {
        return allowedSchedules.clone();
    }
    
    public void setAllowedSchedules(byte[] allowedSchedules) {
        if (allowedSchedules == null || allowedSchedules.length != 3) {
            throw new IllegalArgumentException("Allowed schedules must be exactly 3 bytes");
        }
        this.allowedSchedules = allowedSchedules.clone();
    }
    
    /**
     * Установка битовой маски расписаний из hex строки.
     * Например, "FFFFF0" означает все 20 расписаний разрешены.
     */
    public void setAllowedSchedulesHex(String hex) {
        this.allowedSchedules = hexStringToBytes(hex, 3);
    }
    
    public String getAllowedSchedulesHex() {
        StringBuilder sb = new StringBuilder();
        for (byte b : allowedSchedules) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }
    
    public byte getBehavior() {
        return behavior;
    }
    
    public void setBehavior(byte behavior) {
        if (behavior < 0 || behavior > 2) {
            throw new IllegalArgumentException("Behavior must be 0, 1, or 2");
        }
        this.behavior = behavior;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CardData cardData = (CardData) o;
        return behavior == cardData.behavior &&
               java.util.Arrays.equals(uid, cardData.uid) &&
               java.util.Arrays.equals(allowedSchedules, cardData.allowedSchedules);
    }
    
    @Override
    public int hashCode() {
        int result = Objects.hash(behavior);
        result = 31 * result + java.util.Arrays.hashCode(uid);
        result = 31 * result + java.util.Arrays.hashCode(allowedSchedules);
        return result;
    }
    
    @Override
    public String toString() {
        return "CardData{" +
                "uid=" + getUidHex() +
                ", allowedSchedules=" + getAllowedSchedulesHex() +
                ", behavior=" + behavior +
                '}';
    }
}

