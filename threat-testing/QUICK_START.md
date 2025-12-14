# Быстрый старт - Тестирование угроз безопасности

## Шаг 1: Подготовка окружения

1. Убедитесь что PostgreSQL запущен:
```bash
psql --version
```

2. Создайте тестовую БД (если еще не создана):
```bash
psql -U postgres -c "CREATE DATABASE test_mqtt;"
```

3. Убедитесь что сертификаты сгенерированы (см. WINDOWS_CMDS_USED.txt):
- `server-keystore.p12`
- `broker-truststore.p12`
- `client.p12`
- `client-truststore.p12`

## Шаг 2: Сборка проекта

```bash
cd DICSS
mvn clean package -DskipTests
```

## Шаг 3: Запуск тестов

### Вариант 1: Запуск всех тестов (рекомендуется)

```bash
java -Dtest.db.url=jdbc:postgresql://localhost:5432/test_mqtt \
     -jar threat-testing/target/threat-testing-1.0-SNAPSHOT.jar
```

### Вариант 2: Запуск с полными параметрами

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

## Шаг 4: Анализ результатов

После выполнения тестов вы увидите:
- Сводный отчет по всем тестам
- Статус каждого теста (PASSED/FAILED/ERROR)
- Детальные результаты проверок

## Что проверяют тесты

1. **ThreatTest6**: Защита от несанкционированных подключений (mTLS)
2. **ThreatTest12**: Отключение plaintext порта 1883
3. **ThreatTest14**: Защита от подписки на все топики
4. **ThreatTest30**: Шифрование всех MQTT сообщений
5. **ThreatTest38**: Защита от несанкционированного управления

## Ожидаемый результат

Все тесты должны пройти (PASSED), что означает:
- Защитные механизмы работают корректно
- Система защищена от протестированных угроз
- Угрозы успешно блокируются

## Устранение проблем

### Ошибка подключения к БД
- Проверьте что PostgreSQL запущен
- Проверьте что БД `test_mqtt` создана
- Проверьте параметры подключения

### Ошибка подключения к брокеру
- Убедитесь что сертификаты на месте
- Проверьте пути к keystore/truststore файлам
- Проверьте пароли сертификатов

### Тест не проходит
- Проверьте логи теста
- Убедитесь что брокер запущен
- Проверьте конфигурацию системы

## Дополнительная информация

- Подробная документация: [THREAT_TEST_DOCUMENTATION.md](THREAT_TEST_DOCUMENTATION.md)
- Общая документация: [README.md](README.md)

