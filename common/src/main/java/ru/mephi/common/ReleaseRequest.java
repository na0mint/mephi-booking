package ru.mephi.common;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record ReleaseRequest(@NotNull UUID bookingId) {
}
