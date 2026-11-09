package ru.mephi.hotel;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.common.*;
@Service
public class AllocationService {
    private static final Logger log = LoggerFactory.getLogger(AllocationService.class);
    private final GuardRepository guard;
    private final AllocationRepository allocations;
    private final CatalogService catalog;
    public AllocationService(
            GuardRepository guard, AllocationRepository allocations, CatalogService catalog) {
        this.guard = guard;
        this.allocations = allocations;
        this.catalog = catalog;
    }
    @Transactional
    public RoomView confirm(Long roomId, Long hotelId, SlotRequest request) {
        request.validateDates();
        guard.lockInventory();
        var old = allocations.findById(request.bookingId());
        if (old.isPresent()) {
            Allocation a = old.get();
            if (a.state == Allocation.State.RELEASED)
                throw ApiException.conflict("This booking was already released");
            if (!Objects.equals(a.startDate, request.startDate())
                    || !Objects.equals(a.endDate, request.endDate())
                    || (roomId != null && !a.room.getId().equals(roomId))
                    || (hotelId != null && !a.room.getHotelId().equals(hotelId)))
                throw ApiException.conflict("bookingId reused with a different payload");
            log.info("event=allocation_replayed bookingId={}", request.bookingId());
            return a.room.view();
        }
        Room room;
        if (roomId == null) {
            var candidates = catalog.candidates(hotelId, request.startDate(), request.endDate());
            if (candidates.isEmpty())
                throw ApiException.conflict("No rooms available for requested dates");
            room = candidates.get(0);
        } else {
            room = catalog.requireRoom(roomId);
            if (!room.isAvailable() || allocations.overlaps(roomId, request.startDate(), request.endDate()))
                throw ApiException.conflict("Room is unavailable for requested dates");
        }
        allocations.save(Allocation.held(room, request));
        room.addBooking();
        log.info("event=allocation_held bookingId={} roomId={} timesBooked={}", request.bookingId(),
                room.getId(), room.getTimesBooked());
        return room.view();
    }
    @Transactional
    public void release(Long roomId, UUID bookingId) {
        guard.lockInventory();
        var old = allocations.findById(bookingId);
        if (old.isEmpty()) {
            allocations.save(Allocation.tombstone(bookingId));
            log.info("event=release_tombstone bookingId={}", bookingId);
            return;
        }
        var a = old.get();
        if (roomId != null && a.room != null && !a.room.getId().equals(roomId))
            throw ApiException.conflict("Allocation belongs to a different room");
        if (a.state == Allocation.State.RELEASED)
            return;
        a.state = Allocation.State.RELEASED;
        a.updatedAt = Instant.now();
        a.room.removeBooking();
        log.info("event=allocation_released bookingId={} roomId={}", bookingId, a.room.getId());
    }
}
