# Текстовая схема сети DICSS (блоки и стрелки для ручного отрисовывания)

Используй как шаблон: прямоугольники = блоки, строки ниже = связи/подписи. Угрозы отмечены [T6][T12][T14][T30][T38].

## Блоки (подписи для прямоугольников)
- Внешняя сеть  
  Смартфон/ПК  
  HTTPS

- Роутер + FW  
  NAT, DHCP 67/68  
  WPA3 + MAC whitelist  
  Default deny входящего  
  Белый IP (P2P/VPN)

- Wi-Fi контроллер/AP  
  WPA3, WPS off

- IoT сегмент (VLAN/SSID DICSS-IoT)  
  IoT устройства  
  TEMP_SENSOR / SMART_PLUG / ENERGY_SENSOR / SMART_SWITCH  
  MQTT mTLS 8884  
  Топики: register | health | telemetry | cmd(read)

- Основная LAN (192.168.1.0/24)  
  Demo Runner  
  Embedded Broker + Registry  
  Simulated devices  
  ControllerWebServer 11080 (localhost mTLS)  
  CertRotationService

- Основная LAN (192.168.1.0/24)  
  Пользовательский ПК  
  HTTP 9080 (локально)

- Основная LAN (192.168.1.0/24)  
  Threat-testing jar  
  имитация T6/T12/T14/T30/T38

- Контроллер умного дома (192.168.1.50)  
  ControllerWebServer :11080 HTTPS+mTLS (localhost)

- Контроллер умного дома — Сервисы:
  - Встроенный брокер (Moquette)  
    :8884 mTLS (ClientAuth.REQUIRE)  
    :1883 OFF  
    ACL DeviceAuthorizatorPolicy  
    [T6][T12][T14][T30][T38]
  - Реестр устройств (PostgreSQL/H2)  
    devices / telemetry / alerts
  - MQTT-клиент Payara  
    Паблишер/Сабскрайбер → JMS
  - Сервер Payara 6  
    REST :9080 (prod→HTTPS)  
    Админ :9048 local  
    JMS очередь jms/telemetryQueue

- PostgreSQL :5432 (localhost)

## Связи (стрелки/линии с подписями)
- Смартфон/ПК HTTPS → (P2P/VPN HTTPS :9080, если проброшено) → Роутер + FW
- Роутер + FW → Wi-Fi контроллер/AP
- Wi-Fi контроллер/AP → (WPA3 + MAC фильтрация) → IoT устройства (IoT сегмент)
- IoT устройства → (MQTT mTLS :8884, home/<ctrl>/devices/<serial>/{register|health|telemetry|cmd(read)}) → Встроенный брокер (Moquette)
- Роутер + FW → (LAN HTTP :9080 / MQTT :8884) → Контроллер умного дома
- Пользовательский ПК → (HTTP :9080, локально) → Сервер Payara 6
- Demo Runner → Встроенный брокер (Moquette)
- Demo Runner → Реестр устройств
- Demo Runner → ControllerWebServer :11080
- Threat-testing jar → (тесты угроз → MQTT/REST) → Контроллер умного дома [T6][T12][T14][T30][T38]
- Встроенный брокер (Moquette) → (Приём, ACL, детекция клонов) → Реестр устройств [T14][T38]
- Встроенный брокер (Moquette) → (Публикация телеметрии) → MQTT-клиент Payara
- MQTT-клиент Payara → (JMS: публикация телеметрии) → Сервер Payara 6
- Сервер Payara 6 → (Доступ к БД) → PostgreSQL :5432
- Реестр устройств → PostgreSQL :5432

## Где актуальны угрозы
- [T6] Несанкционированное подключение к MQTT: граница Встроенный брокер :8884 (mTLS/ClientAuth), риск при компрометации клиентских сертификатов или снятии ClientAuth.
- [T12] Перехват MQTT без TLS: закрытый :1883; риск только при отключённом TLS или его неверной конфигурации.
- [T14] Subscribe-to-all: внутри ACL брокера при подписке на wildcard (#, +/#); защита DeviceAuthorizatorPolicy.
- [T30] Утечка данных через MQTT топики: метаданные видны, payload защищён TLS; риск при снятии TLS/ACL или компрометации брокера/клиентов.
- [T38] Несанкционированное управление через MQTT: публикация в /cmd; блокируется ACL (DeviceAuthorizatorPolicy), риск при обходе ACL или компрометации контроллера/админ-клиента.
