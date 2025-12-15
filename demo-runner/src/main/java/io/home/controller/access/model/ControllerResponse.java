package io.home.controller.access.model;

import java.util.Objects;

/**
 * Модель ответа от контроллера доступа.
 * Формат: "State/{serial} Replay {type} {data}"
 */
public class ControllerResponse {
    
    public enum ResponseType {
        FLASH_WR,
        Time_WR,
        SCHEDULE_WR,
        Cart_resiv,
        UNKNOWN
    }
    
    private int serialNumber;
    private ResponseType type;
    private String rawMessage;
    private Integer sectorNumber; // для FLASH_WR
    private String cardUid; // для Cart_resiv (4 байта hex)
    private Integer cardStatus; // для Cart_resiv (0-4)
    
    public ControllerResponse() {
        this.type = ResponseType.UNKNOWN;
    }
    
    public ControllerResponse(String rawMessage) {
        this.rawMessage = rawMessage;
        parseMessage(rawMessage);
    }
    
    /**
     * Парсинг ответа контроллера.
     * Форматы:
     * - "State/1 Replay FLASH_WR X"
     * - "State/1 Replay Time_WR"
     * - "State/1 Replay SCHEDULE_WR"
     * - "State/1 Replay Cart_resiv 01 02 03 04 XX"
     */
    private void parseMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            this.type = ResponseType.UNKNOWN;
            return;
        }
        
        // Парсинг формата: State/{serial} Replay {type} {data}
        String[] parts = message.trim().split("\\s+");
        
        if (parts.length < 3) {
            this.type = ResponseType.UNKNOWN;
            return;
        }
        
        // Извлечение серийного номера из "State/{serial}"
        if (parts[0].startsWith("State/")) {
            try {
                this.serialNumber = Integer.parseInt(parts[0].substring(6));
            } catch (NumberFormatException e) {
                this.serialNumber = 0;
            }
        }
        
        // Проверка наличия "Replay"
        if (!parts[1].equals("Replay")) {
            this.type = ResponseType.UNKNOWN;
            return;
        }
        
        // Определение типа ответа
        String typeStr = parts[2];
        switch (typeStr) {
            case "FLASH_WR":
                this.type = ResponseType.FLASH_WR;
                if (parts.length > 3) {
                    try {
                        this.sectorNumber = Integer.parseInt(parts[3]);
                    } catch (NumberFormatException e) {
                        // игнорируем
                    }
                }
                break;
            case "Time_WR":
                this.type = ResponseType.Time_WR;
                break;
            case "SCHEDULE_WR":
                this.type = ResponseType.SCHEDULE_WR;
                break;
            case "Cart_resiv":
                this.type = ResponseType.Cart_resiv;
                // Формат: Cart_resiv 01 02 03 04 XX
                // parts[0]="State/1", parts[1]="Replay", parts[2]="Cart_resiv", 
                // parts[3]="01", parts[4]="02", parts[5]="03", parts[6]="04", parts[7]="XX"
                if (parts.length >= 8) {
                    // UID карты: 4 байта hex (parts[3-6])
                    this.cardUid = parts[3] + parts[4] + parts[5] + parts[6];
                    // Статус: последний байт (parts[7])
                    try {
                        this.cardStatus = Integer.parseInt(parts[7], 16);
                    } catch (NumberFormatException e) {
                        // игнорируем
                    }
                }
                break;
            default:
                this.type = ResponseType.UNKNOWN;
        }
    }
    
    public int getSerialNumber() {
        return serialNumber;
    }
    
    public void setSerialNumber(int serialNumber) {
        this.serialNumber = serialNumber;
    }
    
    public ResponseType getType() {
        return type;
    }
    
    public void setType(ResponseType type) {
        this.type = type;
    }
    
    public String getRawMessage() {
        return rawMessage;
    }
    
    public void setRawMessage(String rawMessage) {
        this.rawMessage = rawMessage;
        if (rawMessage != null) {
            parseMessage(rawMessage);
        }
    }
    
    public Integer getSectorNumber() {
        return sectorNumber;
    }
    
    public void setSectorNumber(Integer sectorNumber) {
        this.sectorNumber = sectorNumber;
    }
    
    public String getCardUid() {
        return cardUid;
    }
    
    public void setCardUid(String cardUid) {
        this.cardUid = cardUid;
    }
    
    public Integer getCardStatus() {
        return cardStatus;
    }
    
    public void setCardStatus(Integer cardStatus) {
        this.cardStatus = cardStatus;
    }
    
    /**
     * Получение текстового описания статуса карты.
     */
    public String getCardStatusDescription() {
        if (cardStatus == null) {
            return "Unknown";
        }
        switch (cardStatus) {
            case 0: return "Заблокирована";
            case 1: return "Не подошла по расписанию";
            case 2: return "Сработала от первого считывателя (реле 1)";
            case 3: return "Сработала от второго считывателя (реле 2)";
            case 4: return "Сработала от любого считывателя (оба реле)";
            default: return "Неизвестный статус: " + cardStatus;
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ControllerResponse that = (ControllerResponse) o;
        return serialNumber == that.serialNumber &&
               type == that.type &&
               Objects.equals(rawMessage, that.rawMessage) &&
               Objects.equals(sectorNumber, that.sectorNumber) &&
               Objects.equals(cardUid, that.cardUid) &&
               Objects.equals(cardStatus, that.cardStatus);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(serialNumber, type, rawMessage, sectorNumber, cardUid, cardStatus);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ControllerResponse{");
        sb.append("serial=").append(serialNumber);
        sb.append(", type=").append(type);
        if (sectorNumber != null) {
            sb.append(", sector=").append(sectorNumber);
        }
        if (cardUid != null) {
            sb.append(", cardUid=").append(cardUid);
        }
        if (cardStatus != null) {
            sb.append(", cardStatus=").append(cardStatus)
              .append(" (").append(getCardStatusDescription()).append(")");
        }
        sb.append("}");
        return sb.toString();
    }
}

