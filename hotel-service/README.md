# hotel-service

Порт 8081. CatalogService управляет каталогом и свободными номерами, AllocationService атомарно подтверждает/освобождает слоты, StatisticsService считает загрузку. JPA allocation_guard защищает выбор и проверку пересечений. Сервисные методы требуют INTERNAL JWT; административные — ADMIN. Хранилище jdbc:h2:mem:hotelservice.

Сборка из корня: `mvn -s .mvn/settings.xml -pl hotel-service -am verify`.

Приложение запускается из `target/hotel-service-1.0.0-SNAPSHOT-exec.jar`.

Полные инструкции, API, безопасность и тестирование: [общий README](../README.md).

[Схема БД](../docs/database.md) · [ADR](../docs/adr/) · [Критерии](../docs/criteria.md).
