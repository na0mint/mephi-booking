# booking-service

Порт 8082. Пользователи и JWT, личные брони, requestId, пагинация. BookingWorkflow оркестрирует HotelPort; BookingTransactions фиксирует локальные состояния; SagaRecovery повторяет PENDING с истёкшим lease и отложенные компенсации. HttpHotelClient использует LoadBalanced RestClient, connect/read timeout, Spring Retry и независимые Circuit Breaker. Хранилище jdbc:h2:mem:bookingservice.

Сборка из корня: `mvn -s .mvn/settings.xml -pl booking-service -am verify`.

Приложение запускается из `target/booking-service-1.0.0-SNAPSHOT-exec.jar`.

Полные инструкции, API, безопасность и тестирование: [общий README](../README.md).

[Схема БД](../docs/database.md) · [ADR](../docs/adr/) · [Критерии](../docs/criteria.md).
