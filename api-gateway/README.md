# api-gateway

Порт 8080. WebFlux Spring Cloud Gateway. Явные lb://booking-service и lb://hotel-service маршруты разрешаются через Eureka. JwtDecoder проверяет токен, GatewaySecurity ограничивает роли и запрещает внутренние методы, GatewayTraceFilter передаёт X-Trace-Id, GatewayErrors формирует единый ErrorDto. Bearer передаётся backend без изменений.

Сборка из корня: `mvn -s .mvn/settings.xml -pl api-gateway -am verify`.

Приложение запускается из `target/api-gateway-1.0.0-SNAPSHOT-exec.jar`.

Полные инструкции, API, безопасность и тестирование: [общий README](../README.md).

[Схема БД](../docs/database.md) · [ADR](../docs/adr/) · [Критерии](../docs/criteria.md).
