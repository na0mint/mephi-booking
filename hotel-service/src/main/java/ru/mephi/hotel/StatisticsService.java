package ru.mephi.hotel;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly = true)
public class StatisticsService {
    public record RoomStats(long roomId, long hotelId, String number, boolean available, long timesBooked,
            long reservationsInPeriod, long occupiedNights, long periodNights, double occupancyRate) {
    }
    public record Report(LocalDate startDate, LocalDate endDate, long totalRooms, long operationalRooms,
            long totalOccupiedNights, double occupancyRate, List<RoomStats> rooms) {
    }
    private final RoomRepository rooms;
    private final AllocationRepository allocations;
    public StatisticsService(RoomRepository rooms, AllocationRepository allocations) {
        this.rooms = rooms;
        this.allocations = allocations;
    }
    public Report report(Long hotelId, LocalDate start, LocalDate end) {
        CatalogService.validatePeriod(start, end);
        var selected = rooms.findAll(Sort.by("timesBooked", "id"))
                               .stream()
                               .filter(r -> hotelId == null || r.getHotelId().equals(hotelId))
                               .toList();
        var held = allocations.inPeriod(hotelId, start, end)
                           .stream()
                           .collect(java.util.stream.Collectors.groupingBy(a -> a.room.getId()));
        long nights = ChronoUnit.DAYS.between(start, end);
        var stats =
                selected.stream()
                        .map(r -> {
                            var matches = held.getOrDefault(r.getId(), java.util.List.of());
                            long occupied =
                                    matches.stream()
                                            .mapToLong(a
                                                    -> ChronoUnit.DAYS.between(
                                                            a.startDate.isBefore(start) ? start : a.startDate,
                                                            a.endDate.isAfter(end) ? end : a.endDate))
                                            .sum();
                            return new RoomStats(r.getId(), r.getHotelId(), r.getNumber(), r.isAvailable(),
                                    r.getTimesBooked(), matches.size(), occupied, nights,
                                    (double) occupied / nights);
                        })
                        .toList();
        long occupied = stats.stream().mapToLong(RoomStats::occupiedNights).sum();
        long operational = selected.stream().filter(r -> r.isAvailable()).count();
        return new Report(start, end, selected.size(), operational, occupied,
                selected.isEmpty() ? 0 : (double) occupied / (selected.size() * nights), stats);
    }
}
