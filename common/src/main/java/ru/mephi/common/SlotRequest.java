package ru.mephi.common;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;
public record SlotRequest(@NotNull UUID bookingId, @NotNull LocalDate startDate, @NotNull LocalDate endDate) {
    public void validateDates() {
        if (startDate == null || endDate == null || !endDate.isAfter(startDate))
            throw new ApiException(400, "INVALID_DATES", "endDate must be after startDate");
    }
}
