package ru.mephi.hotel;

import static ru.mephi.hotel.CatalogDtos.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.mephi.common.*;
@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class CatalogController {
    private final CatalogService catalog;
    private final StatisticsService statistics;
    public CatalogController(CatalogService catalog, StatisticsService statistics) {
        this.catalog = catalog;
        this.statistics = statistics;
    }
    @GetMapping("/hotels")
    public PageDto<HotelView> hotels(@RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return catalog.hotels(name, page, size);
    }
    @GetMapping("/hotels/{id}")
    public HotelView hotel(@PathVariable long id) {
        return catalog.hotel(id);
    }
    @PostMapping("/hotels")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public HotelView createHotel(@Valid @RequestBody HotelInput input) {
        return catalog.createHotel(input);
    }
    @PutMapping("/hotels/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public HotelView updateHotel(@PathVariable long id, @Valid @RequestBody HotelInput input) {
        return catalog.updateHotel(id, input);
    }
    @DeleteMapping("/hotels/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteHotel(@PathVariable long id) {
        catalog.deleteHotel(id);
    }
    @PostMapping("/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public RoomView createRoom(@Valid @RequestBody RoomInput input) {
        return catalog.createRoom(input);
    }
    @PutMapping("/rooms/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public RoomView updateRoom(@PathVariable long id, @Valid @RequestBody RoomInput input) {
        return catalog.updateRoom(id, input);
    }
    @DeleteMapping("/rooms/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteRoom(@PathVariable long id) {
        catalog.deleteRoom(id);
    }
    @GetMapping("/rooms/{id}")
    public RoomView room(@PathVariable long id) {
        return catalog.room(id);
    }
    @GetMapping("/rooms")
    public PageDto<RoomView> rooms(@RequestParam(required = false) Long hotelId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return catalog.freeRooms(hotelId, startDate, endDate, false, page, size);
    }
    @GetMapping("/rooms/recommend")
    public PageDto<RoomView> recommend(@RequestParam(required = false) Long hotelId,
            @RequestParam LocalDate startDate, @RequestParam LocalDate endDate,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return catalog.freeRooms(hotelId, startDate, endDate, true, page, size);
    }
    @GetMapping({"/rooms/statistics", "/hotels/statistics"})
    @PreAuthorize("hasRole('ADMIN')")
    public StatisticsService.Report statistics(@RequestParam(required = false) Long hotelId,
            @RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        return statistics.report(hotelId, startDate, endDate);
    }
}
