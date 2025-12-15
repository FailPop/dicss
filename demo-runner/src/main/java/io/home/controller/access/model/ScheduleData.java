package io.home.controller.access.model;

import java.util.Objects;

/**
 * Модель данных расписания для контроллера доступа.
 * Максимум 20 расписаний (номера 1-20).
 */
public class ScheduleData {
    
    private int scheduleNumber; // 1-20
    private int startHour; // 0-23
    private int startMinute; // 0-59
    private int endHour; // 0-23
    private int endMinute; // 0-59
    private byte daysMask; // битовая маска дней недели
    
    public ScheduleData() {
        this.scheduleNumber = 1;
        this.startHour = 0;
        this.startMinute = 0;
        this.endHour = 23;
        this.endMinute = 59;
        this.daysMask = 0;
    }
    
    public ScheduleData(int scheduleNumber, int startHour, int startMinute, 
                       int endHour, int endMinute, byte daysMask) {
        setScheduleNumber(scheduleNumber);
        setStartHour(startHour);
        setStartMinute(startMinute);
        setEndHour(endHour);
        setEndMinute(endMinute);
        this.daysMask = daysMask;
    }
    
    /**
     * Установка дня недели в битовой маске.
     * @param dayOfWeek 1=понедельник, 2=вторник, ..., 7=воскресенье
     * @param enabled true для включения дня
     */
    public void setDay(int dayOfWeek, boolean enabled) {
        if (dayOfWeek < 1 || dayOfWeek > 7) {
            throw new IllegalArgumentException("Day of week must be 1-7");
        }
        int bit = dayOfWeek - 1; // бит 0 = понедельник, бит 6 = воскресенье
        if (enabled) {
            daysMask |= (1 << bit);
        } else {
            daysMask &= ~(1 << bit);
        }
    }
    
    /**
     * Проверка включен ли день недели.
     */
    public boolean isDayEnabled(int dayOfWeek) {
        if (dayOfWeek < 1 || dayOfWeek > 7) {
            return false;
        }
        int bit = dayOfWeek - 1;
        return (daysMask & (1 << bit)) != 0;
    }
    
    /**
     * Установка всех дней недели.
     * @param days массив дней (1=понедельник, ..., 7=воскресенье)
     */
    public void setDays(int... days) {
        daysMask = 0;
        for (int day : days) {
            setDay(day, true);
        }
    }
    
    public int getScheduleNumber() {
        return scheduleNumber;
    }
    
    public void setScheduleNumber(int scheduleNumber) {
        if (scheduleNumber < 1 || scheduleNumber > 20) {
            throw new IllegalArgumentException("Schedule number must be 1-20");
        }
        this.scheduleNumber = scheduleNumber;
    }
    
    public int getStartHour() {
        return startHour;
    }
    
    public void setStartHour(int startHour) {
        if (startHour < 0 || startHour > 23) {
            throw new IllegalArgumentException("Hour must be 0-23");
        }
        this.startHour = startHour;
    }
    
    public int getStartMinute() {
        return startMinute;
    }
    
    public void setStartMinute(int startMinute) {
        if (startMinute < 0 || startMinute > 59) {
            throw new IllegalArgumentException("Minute must be 0-59");
        }
        this.startMinute = startMinute;
    }
    
    public int getEndHour() {
        return endHour;
    }
    
    public void setEndHour(int endHour) {
        if (endHour < 0 || endHour > 23) {
            throw new IllegalArgumentException("Hour must be 0-23");
        }
        this.endHour = endHour;
    }
    
    public int getEndMinute() {
        return endMinute;
    }
    
    public void setEndMinute(int endMinute) {
        if (endMinute < 0 || endMinute > 59) {
            throw new IllegalArgumentException("Minute must be 0-59");
        }
        this.endMinute = endMinute;
    }
    
    public byte getDaysMask() {
        return daysMask;
    }
    
    public void setDaysMask(byte daysMask) {
        this.daysMask = daysMask;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScheduleData that = (ScheduleData) o;
        return scheduleNumber == that.scheduleNumber &&
               startHour == that.startHour &&
               startMinute == that.startMinute &&
               endHour == that.endHour &&
               endMinute == that.endMinute &&
               daysMask == that.daysMask;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(scheduleNumber, startHour, startMinute, endHour, endMinute, daysMask);
    }
    
    @Override
    public String toString() {
        StringBuilder days = new StringBuilder();
        String[] dayNames = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        for (int i = 1; i <= 7; i++) {
            if (isDayEnabled(i)) {
                if (days.length() > 0) days.append(",");
                days.append(dayNames[i - 1]);
            }
        }
        return String.format("ScheduleData{number=%d, time=%02d:%02d-%02d:%02d, days=[%s]}",
                scheduleNumber, startHour, startMinute, endHour, endMinute, days.toString());
    }
}

