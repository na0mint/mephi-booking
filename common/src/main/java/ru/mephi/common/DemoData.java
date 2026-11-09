package ru.mephi.common;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
/** Coordinated fixtures used independently by both databases, without startup HTTP calls. */
public final class DemoData {
    private DemoData() {
    }
    public record Reservation(UUID bookingId, UUID requestId, String username, long roomId,
            LocalDate startDate, LocalDate endDate, boolean cancelled) {
    }
    public static List<Reservation> reservations() {
        LocalDate day = LocalDate.now(ZoneOffset.UTC);
        return List.of(sample(1, "alice", 1, day.plusDays(10), false),
                sample(2, "bob", 1, day.plusDays(20), false), sample(3, "alice", 2, day.plusDays(10), false),
                sample(4, "alice", 3, day.plusDays(15), true));
    }
    private static Reservation sample(
            int id, String username, long roomId, LocalDate start, boolean cancelled) {
        return new Reservation(UUID.fromString("00000000-0000-0000-0000-00000000000" + id),
                UUID.fromString("10000000-0000-0000-0000-00000000000" + id), username, roomId, start,
                start.plusDays(2), cancelled);
    }
}
