package ru.mephi.hotel;

import static ru.mephi.hotel.CatalogDtos.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.common.*;
@Service
@Transactional(readOnly = true)
public class CatalogService {
    private final HotelRepository hotels;
    private final RoomRepository rooms;
    private final AllocationRepository allocations;
    private final GuardRepository guard;
    public CatalogService(HotelRepository hotels, RoomRepository rooms, AllocationRepository allocations,
            GuardRepository guard) {
        this.hotels = hotels;
        this.rooms = rooms;
        this.allocations = allocations;
        this.guard = guard;
    }
    public PageDto<HotelView> hotels(String name, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("id"));
        var all = hotels.findAll(pageable);
        if (name == null || name.isBlank())
            return PageDto.from(all.map(HotelView::from));
        return PageDto.from(hotelsByName(name, pageable).map(HotelView::from));
    }
    private org.springframework.data.domain.Page<Hotel> hotelsByName(
            String name, org.springframework.data.domain.Pageable pageable) {
        return hotels.findByNameContainingIgnoreCase(name, pageable);
    }
    public HotelView hotel(long id) {
        return HotelView.from(requireHotel(id));
    }
    @Transactional
    public HotelView createHotel(HotelInput input) {
        return HotelView.from(hotels.save(new Hotel(input.name(), input.address())));
    }
    @Transactional
    public HotelView updateHotel(long id, HotelInput input) {
        guard.lockInventory();
        var h = requireHotel(id);
        h.name = input.name();
        h.address = input.address();
        return HotelView.from(h);
    }
    @Transactional
    public void deleteHotel(long id) {
        guard.lockInventory();
        var h = requireHotel(id);
        if (rooms.existsByHotelId(id))
            throw ApiException.conflict("Delete hotel rooms first");
        hotels.delete(h);
    }
    @Transactional
    public RoomView createRoom(RoomInput input) {
        guard.lockInventory();
        return rooms.saveAndFlush(new Room(requireHotel(input.hotelId()), input.number(), input.available()))
                .view();
    }
    @Transactional
    public RoomView updateRoom(long id, RoomInput input) {
        guard.lockInventory();
        var r = requireRoom(id);
        if (!r.getHotelId().equals(input.hotelId()))
            throw ApiException.conflict("Moving rooms between hotels is not supported");

        r.update(input.number(), input.available());
        rooms.flush();
        return r.view();
    }
    @Transactional
    public void deleteRoom(long id) {
        guard.lockInventory();
        var r = requireRoom(id);
        if (allocations.existsByRoomId(id))
            throw ApiException.conflict("Room has allocation history; set available=false instead");
        rooms.delete(r);
    }
    public RoomView room(long id) {
        return requireRoom(id).view();
    }
    public PageDto<RoomView> freeRooms(
            Long hotelId, LocalDate startDate, LocalDate endDate, boolean recommended, int page, int size) {
        validatePeriod(startDate, endDate);
        return PageDto.from(
                rooms.findAll(freeSpec(hotelId, startDate, endDate),
                             PageRequest.of(
                                     page, size, recommended ? Sort.by("timesBooked", "id") : Sort.by("id")))
                        .map(Room::view));
    }
    public List<Room> candidates(Long hotelId, LocalDate startDate, LocalDate endDate) {
        return rooms.findAll(freeSpec(hotelId, startDate, endDate), Sort.by("timesBooked", "id"));
    }
    private Specification<Room> freeSpec(Long hotelId, LocalDate startDate, LocalDate endDate) {
        return (r, q, b) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(b.isTrue(r.get("available")));
            if (hotelId != null)
                predicates.add(b.equal(r.get("hotel").get("id"), hotelId));
            if (startDate != null) {
                var sub = q.subquery(Integer.class);
                var a = sub.from(Allocation.class);
                sub.select(b.literal(1))
                        .where(b.equal(a.get("room"), r), b.equal(a.get("state"), Allocation.State.HELD),
                                b.lessThan(a.get("startDate"), endDate),
                                b.greaterThan(a.get("endDate"), startDate));
                predicates.add(b.not(b.exists(sub)));
            }
            return b.and(predicates.toArray(jakarta.persistence.criteria.Predicate[] ::new));
        };
    }
    public static void validatePeriod(LocalDate start, LocalDate end) {
        if ((start == null) != (end == null) || (start != null && !end.isAfter(start)))
            throw new ApiException(400, "INVALID_DATES", "Provide both dates, with endDate after startDate");
    }
    Hotel requireHotel(long id) {
        return hotels.findById(id).orElseThrow(() -> ApiException.notFound("Hotel"));
    }
    Room requireRoom(long id) {
        return rooms.findById(id).orElseThrow(() -> ApiException.notFound("Room"));
    }
}
