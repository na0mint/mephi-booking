# common

Общая библиотека: транспортные records, ErrorDto/ApiAdvice, stateless MVC Security, JWT encoder/decoder, безопасный traceId, MDC scope и согласованные demo fixtures. JPA entities и бизнес-репозитории остаются в своих сервисах. Gateway исключает spring-webmvc из common, импортирует только JWT-конфигурацию и использует собственный WebFlux Security.

Сборка из корня: `mvn -s .mvn/settings.xml -pl common -am verify`.

Полные инструкции, API, безопасность и тестирование: [общий README](../README.md).

[Схема БД](../docs/database.md) · [ADR](../docs/adr/) · [Критерии](../docs/criteria.md).
