# Модуль тестирования угроз безопасности DICSS

## Описание

Модуль `threat-testing` предназначен для моделирования и тестирования актуальных угроз безопасности из модели угроз ВКР. Тесты проверяют эффективность защитных механизмов системы DICSS.

## Структура

```
threat-testing/
├── src/main/java/io/home/threat/
│   ├── ThreatTestBase.java                    # Базовый класс для всех тестов
│   ├── ThreatTest6_UnauthorizedMqttConnection.java
│   ├── ThreatTest12_PlaintextPortDisabled.java
│   ├── ThreatTest14_SubscribeToAllAttack.java
│   ├── ThreatTest30_MqttDataLeakage.java
│   ├── ThreatTest38_UnauthorizedControl.java
│   └── ThreatTestRunner.java                  # Запуск всех тестов
├── src/main/resources/
│   └── logback.xml                            # Конфигурация логирования
├── THREAT_TEST_DOCUMENTATION.md              # Подробная документация
└── README.md                                  # Этот файл
```

## Предварительные требования

1. **PostgreSQL** должен быть запущен и доступен
2. **Сертификаты** должны быть сгенерированы:
   - `server-keystore.p12` - серверный keystore
   - `broker-truststore.p12` - truststore брокера
   - `client.p12` - клиентский keystore
   - `client-truststore.p12` - truststore клиента
3. **База данных** должна быть инициализирована (схема создана)

## Сборка

```bash
cd DICSS
mvn clean package -DskipTests
```

Это создаст fat-jar: `threat-testing/target/threat-testing-1.0-SNAPSHOT.jar`

## Запуск тестов

### Запуск всех тестов

```bash
java -jar threat-testing/target/threat-testing-1.0-SNAPSHOT.jar
```

### Запуск с параметрами

```bash
java -Dserver.keystore=server-keystore.p12 \
     -Dserver.keystore.password=changeit \
     -Dbroker.truststore=broker-truststore.p12 \
     -Dbroker.truststore.password=changeit \
     -Dclient.keystore=client.p12 \
     -Dclient.keystore.password=changeit \
     -Dclient.truststore=client-truststore.p12 \
     -Dclient.truststore.password=changeit \
     -Dtest.db.url=jdbc:postgresql://localhost:5432/test_mqtt \
     -jar threat-testing/target/threat-testing-1.0-SNAPSHOT.jar
```

### Запуск отдельного теста

```bash
java -cp threat-testing/target/threat-testing-1.0-SNAPSHOT.jar \
     io.home.threat.ThreatTest6_UnauthorizedMqttConnection
```

## Реализованные тесты

### Фаза 1: Критические MQTT угрозы (Y=1.0)

1. **ThreatTest6** - Несанкционированное подключение к MQTT-брокеру
2. **ThreatTest12** - Перехват MQTT-сообщений при отсутствии TLS
3. **ThreatTest14** - Атака "subscribe to all" на MQTT-брокер
4. **ThreatTest30** - Утечка конфиденциальной информации через MQTT топики
5. **ThreatTest38** - Несанкционированное управление электроснабжением через MQTT

Подробное описание каждого теста см. в [THREAT_TEST_DOCUMENTATION.md](THREAT_TEST_DOCUMENTATION.md)

## Интерпретация результатов

### PASSED (Успешно)
- Защитные механизмы сработали корректно
- Угроза была успешно заблокирована
- Система ведет себя как ожидается

### FAILED (Неуспешно)
- Защитные механизмы не сработали
- Угроза не была заблокирована
- Требуется доработка защитных механизмов

### ERROR (Ошибка)
- Тест завершился с исключением
- Возможны проблемы с окружением или конфигурацией
- Требуется проверка логов

## Логирование

Все действия тестов логируются с уровнем INFO. Детальные логи доступны в консоли.

Для изменения уровня логирования отредактируйте `src/main/resources/logback.xml`.

## Интеграция с системой

Тесты используют существующие компоненты системы:
- `DatabaseManager` - для работы с БД
- `MqttSecureClient` - для MQTT подключений
- `BrokerService` - для управления брокером
- Репозитории и сервисы из `device-registry`

## Изоляция тестов

- Тесты используют отдельную тестовую БД (по умолчанию `test_mqtt`)
- Тестовые устройства создаются и удаляются автоматически
- Брокер запускается автоматически если не запущен

## Дальнейшее развитие

Планируется реализация тестов для:
- Фаза 2: Высокоприоритетные угрозы (Y=0.75)
- Фаза 3: Среднеприоритетные угрозы

## Поддержка

При возникновении проблем:
1. Проверьте логи тестов
2. Убедитесь что все сертификаты на месте
3. Проверьте доступность PostgreSQL
4. См. [THREAT_TEST_DOCUMENTATION.md](THREAT_TEST_DOCUMENTATION.md) для деталей

