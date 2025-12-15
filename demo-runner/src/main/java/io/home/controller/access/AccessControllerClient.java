package io.home.controller.access;

import io.home.client.MqttSecureClient;
import io.home.controller.access.model.CardData;
import io.home.controller.access.model.ControllerResponse;
import io.home.controller.access.model.ScheduleData;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/**
 * Клиент для управления контроллером доступа по картам через MQTT.
 * 
 * Отправляет команды в топик: Comand/{serialNumber}
 * Получает ответы из топика: State/{serialNumber}
 */
public class AccessControllerClient implements AutoCloseable {
    
    private static final Logger logger = LoggerFactory.getLogger(AccessControllerClient.class);
    
    // Протокольные константы
    private static final byte FLASH_START_1 = (byte) 0xda;
    private static final byte FLASH_START_2 = (byte) 0xdb;
    private static final byte FLASH_END_1 = (byte) 0xdd;
    private static final byte FLASH_END_2 = (byte) 0xde;
    
    private static final byte TIME_START_1 = (byte) 0xca;
    private static final byte TIME_START_2 = (byte) 0xcb;
    private static final byte TIME_END_1 = (byte) 0xcd;
    private static final byte TIME_END_2 = (byte) 0xeb;
    
    private static final byte SCHEDULE_START_1 = (byte) 0xba;
    private static final byte SCHEDULE_START_2 = (byte) 0xbb;
    private static final byte SCHEDULE_END_1 = (byte) 0xdd;
    private static final byte SCHEDULE_END_2 = (byte) 0xdb;
    
    private static final int MAX_BLOCKS = 781;
    private static final int BLOCK_SIZE = 512;
    private static final int CARDS_PER_BLOCK = 64; // 512 / 8
    
    private final int serialNumber;
    private final MqttSecureClient mqttClient;
    private final String commandTopic;
    private final String stateTopic;
    
    private Consumer<ControllerResponse> responseListener;
    private Consumer<ControllerResponse> cardReadListener;
    
    public AccessControllerClient(int serialNumber,
                                 String host, int port,
                                 String clientKeystorePath, String clientKeystorePassword,
                                 String clientTruststorePath, String clientTruststorePassword) throws Exception {
        this.serialNumber = serialNumber;
        this.commandTopic = "Comand/" + serialNumber; // Примечание: в протоколе используется "Comand" (без 'd')
        this.stateTopic = "State/" + serialNumber;
        
        this.mqttClient = new MqttSecureClient.Builder()
            .host(host)
            .port(port)
            .clientId("access-controller-" + serialNumber)
            .clientKeystore(clientKeystorePath, clientKeystorePassword)
            .clientTruststore(clientTruststorePath, clientTruststorePassword)
            .cleanSession(true)
            .autoReconnect(true)
            .build();
        
        this.mqttClient.connect();
        subscribeToState();
        
        logger.info("AccessControllerClient initialized for controller serial: {}", serialNumber);
    }
    
    /**
     * Подписка на топик State/{serialNumber} для получения ответов.
     */
    private void subscribeToState() throws MqttException {
        mqttClient.subscribe(stateTopic, (topic, payload) -> {
            logger.debug("Received state message: {}", payload);
            handleStateMessage(payload);
        });
        logger.info("Subscribed to state topic: {}", stateTopic);
    }
    
    /**
     * Обработка сообщений из топика State.
     */
    private void handleStateMessage(String payload) {
        try {
            ControllerResponse response = new ControllerResponse(payload);
            
            // Вызов общего обработчика ответов
            if (responseListener != null) {
                responseListener.accept(response);
            }
            
            // Специальный обработчик для событий прикладывания карты
            if (response.getType() == ControllerResponse.ResponseType.Cart_resiv) {
                logger.info("Card read event: UID={}, Status={} ({})", 
                    response.getCardUid(), 
                    response.getCardStatus(),
                    response.getCardStatusDescription());
                
                if (cardReadListener != null) {
                    cardReadListener.accept(response);
                }
            } else {
                logger.info("Controller response: {}", response);
            }
            
        } catch (Exception e) {
            logger.error("Error processing state message: {}", payload, e);
        }
    }
    
    /**
     * Запись блока памяти с данными карт.
     * 
     * @param blockNumber номер блока (1-781, начинается с 1)
     * @param cards список карт для записи (максимум 64 карты в блоке)
     * @throws MqttException при ошибке отправки
     */
    public void writeFlashBlock(int blockNumber, List<CardData> cards) throws MqttException {
        if (blockNumber < 1 || blockNumber > MAX_BLOCKS) {
            throw new IllegalArgumentException("Block number must be 1-" + MAX_BLOCKS);
        }
        if (cards == null || cards.isEmpty()) {
            throw new IllegalArgumentException("Cards list cannot be null or empty");
        }
        if (cards.size() > CARDS_PER_BLOCK) {
            throw new IllegalArgumentException("Maximum " + CARDS_PER_BLOCK + " cards per block");
        }
        
        // Формирование блока данных (512 байт)
        byte[] blockData = new byte[BLOCK_SIZE];
        int offset = 0;
        
        for (CardData card : cards) {
            byte[] cardBytes = card.toByteArray();
            System.arraycopy(cardBytes, 0, blockData, offset, 8);
            offset += 8;
        }
        // Остальные байты уже заполнены нулями
        
        // Формирование команды
        byte[] command = new byte[2 + 2 + BLOCK_SIZE + 2]; // старт + номер_блока + данные + конец
        int pos = 0;
        
        // Стартовые байты
        command[pos++] = FLASH_START_1;
        command[pos++] = FLASH_START_2;
        
        // Номер блока (2 байта, big-endian, начинается с 1)
        command[pos++] = (byte) ((blockNumber >> 8) & 0xFF);
        command[pos++] = (byte) (blockNumber & 0xFF);
        
        // Данные блока (512 байт)
        System.arraycopy(blockData, 0, command, pos, BLOCK_SIZE);
        pos += BLOCK_SIZE;
        
        // Конечные байты
        command[pos++] = FLASH_END_1;
        command[pos++] = FLASH_END_2;
        
        // Преобразование в hex строку для отправки
        String hexCommand = bytesToHex(command);
        
        logger.info("Sending FLASH_WR command: block={}, cards={}", blockNumber, cards.size());
        mqttClient.publish(commandTopic, hexCommand, 1, false);
    }
    
    /**
     * Запись текущего времени в контроллер.
     * 
     * @param dayOfWeek день недели (1=понедельник, ..., 7=воскресенье)
     * @param hour час (0-23)
     * @param minute минуты (0-59)
     * @param second секунды (0-59)
     * @throws MqttException при ошибке отправки
     */
    public void writeTime(int dayOfWeek, int hour, int minute, int second) throws MqttException {
        if (dayOfWeek < 1 || dayOfWeek > 7) {
            throw new IllegalArgumentException("Day of week must be 1-7");
        }
        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException("Hour must be 0-23");
        }
        if (minute < 0 || minute > 59) {
            throw new IllegalArgumentException("Minute must be 0-59");
        }
        if (second < 0 || second > 59) {
            throw new IllegalArgumentException("Second must be 0-59");
        }
        
        // Формирование команды (2 стартовых + 4 данных + 2 конечных = 8 байт)
        byte[] command = new byte[8];
        int pos = 0;
        
        // Стартовые байты
        command[pos++] = TIME_START_1;
        command[pos++] = TIME_START_2;
        
        // Данные времени
        command[pos++] = (byte) dayOfWeek;
        command[pos++] = (byte) hour;
        command[pos++] = (byte) minute;
        command[pos++] = (byte) second;
        
        // Конечные байты
        command[pos++] = TIME_END_1;
        command[pos++] = TIME_END_2;
        
        String hexCommand = bytesToHex(command);
        
        logger.info("Sending Time_WR command: day={}, time={:02d}:{:02d}:{:02d}", 
            dayOfWeek, hour, minute, second);
        mqttClient.publish(commandTopic, hexCommand, 1, false);
    }
    
    /**
     * Запись времени из LocalDateTime.
     */
    public void writeTime(LocalDateTime dateTime) throws MqttException {
        DayOfWeek dayOfWeek = dateTime.getDayOfWeek();
        int dayValue = dayOfWeek.getValue(); // 1=Monday, 7=Sunday
        writeTime(dayValue, dateTime.getHour(), dateTime.getMinute(), dateTime.getSecond());
    }
    
    /**
     * Запись расписания в контроллер.
     * 
     * @param schedule данные расписания
     * @throws MqttException при ошибке отправки
     */
    public void writeSchedule(ScheduleData schedule) throws MqttException {
        if (schedule == null) {
            throw new IllegalArgumentException("Schedule cannot be null");
        }
        
        // Формирование команды
        byte[] command = new byte[10];
        int pos = 0;
        
        // Стартовые байты
        command[pos++] = SCHEDULE_START_1;
        command[pos++] = SCHEDULE_START_2;
        
        // Номер расписания (2 байта, big-endian)
        int scheduleNum = schedule.getScheduleNumber();
        command[pos++] = (byte) ((scheduleNum >> 8) & 0xFF);
        command[pos++] = (byte) (scheduleNum & 0xFF);
        
        // Время начала
        command[pos++] = (byte) schedule.getStartHour();
        command[pos++] = (byte) schedule.getStartMinute();
        
        // Время окончания
        command[pos++] = (byte) schedule.getEndHour();
        command[pos++] = (byte) schedule.getEndMinute();
        
        // Битовая маска дней
        command[pos++] = schedule.getDaysMask();
        
        // Конечные байты
        command[pos++] = SCHEDULE_END_1;
        command[pos++] = SCHEDULE_END_2;
        
        String hexCommand = bytesToHex(command);
        
        logger.info("Sending SCHEDULE_WR command: {}", schedule);
        mqttClient.publish(commandTopic, hexCommand, 1, false);
    }
    
    /**
     * Установка обработчика всех ответов контроллера.
     */
    public void setResponseListener(Consumer<ControllerResponse> listener) {
        this.responseListener = listener;
    }
    
    /**
     * Установка обработчика событий прикладывания карты.
     */
    public void setCardReadListener(Consumer<ControllerResponse> listener) {
        this.cardReadListener = listener;
    }
    
    /**
     * Преобразование массива байт в hex строку.
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02X", b & 0xFF));
        }
        return hex.toString();
    }
    
    /**
     * Получение серийного номера контроллера.
     */
    public int getSerialNumber() {
        return serialNumber;
    }
    
    /**
     * Проверка подключения к MQTT брокеру.
     */
    public boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }
    
    @Override
    public void close() throws Exception {
        if (mqttClient != null) {
            mqttClient.close();
            logger.info("AccessControllerClient closed for serial: {}", serialNumber);
        }
    }
}

