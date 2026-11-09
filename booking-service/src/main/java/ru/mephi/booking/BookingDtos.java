package ru.mephi.booking;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.UUID;
public final class BookingDtos {
    private BookingDtos() {
    }
    public record CreateBooking(@NotNull UUID requestId, @Positive Long roomId, @Positive Long hotelId,
            boolean autoSelect, @NotNull LocalDate startDate, @NotNull LocalDate endDate) {
        public void validate() {
            if (!endDate.isAfter(startDate))
                throw new ru.mephi.common.ApiException(
                        400, "INVALID_DATES", "endDate must be after startDate");
            if (!autoSelect && roomId == null)
                throw new ru.mephi.common.ApiException(
                        400, "ROOM_REQUIRED", "roomId is required for explicit selection");
            if (java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) > 365)
                throw new ru.mephi.common.ApiException(
                        400, "INVALID_DATES", "Maximum booking length is 365 nights");
        }
    }
    public record BookingView(UUID id, UUID requestId, Long userId, Long roomId, Long hotelId,
            boolean autoSelect, LocalDate startDate, LocalDate endDate, Booking.Status status,
            Instant createdAt, Instant updatedAt, boolean compensationPending, String failureCode,
            String failureReason, String traceId) {
        static BookingView from(Booking b) {
            return new BookingView(b.id, b.requestId, b.user.getId(), b.roomId, b.hotelId, b.autoSelect,
                    b.startDate, b.endDate, b.status, b.createdAt, b.updatedAt, b.compensationPending,
                    b.failureCode, b.failureReason, b.traceId);
        }
    }
    record Work(UUID id, UUID token, Long roomId, Long hotelId, boolean autoSelect, LocalDate startDate,
            LocalDate endDate, String traceId) {
    }
}
