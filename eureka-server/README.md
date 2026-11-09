# eureka-server

Порт 8761. Standalone Spring Cloud Netflix Eureka Server: register-with-eureka=false, fetch-registry=false. UI и реестр предназначены для доверенной локальной сети. Gateway и оба backend автоматически регистрируются и получают адрес реестра из EUREKA_URL.

Сборка из корня: `mvn -s .mvn/settings.xml -pl eureka-server -am verify`.

Приложение запускается из `target/eureka-server-1.0.0-SNAPSHOT-exec.jar`.

Полные инструкции, API, безопасность и тестирование: [общий README](../README.md).

[Схема БД](../docs/database.md) · [ADR](../docs/adr/) · [Критерии](../docs/criteria.md).
