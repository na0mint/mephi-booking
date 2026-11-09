package ru.mephi.booking;

import static ru.mephi.booking.BookingDtos.*;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ru.mephi.common.*;
@RestController
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class BookingController {
    private final BookingWorkflow workflow;
    private final BookingTransactions transactions;
    private final UserService users;
    private final HotelPort hotel;
    public BookingController(
            BookingWorkflow workflow, BookingTransactions transactions, UserService users, HotelPort hotel) {
        this.workflow = workflow;
        this.transactions = transactions;
        this.users = users;
        this.hotel = hotel;
    }
    @ApiResponse(responseCode = "201", description = "Booking confirmed",
            content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = BookingView.class)))
    @ApiResponse(responseCode = "202", description = "The same request is being processed",
            content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = BookingView.class)))
    @ApiResponse(responseCode = "409", description = "Room unavailable or idempotency conflict",
            content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "503", description = "Hotel unavailable; bookingId is in fields",
            content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @PostMapping({"/booking", "/api/bookings"})
    public ResponseEntity<?>
    create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateBooking input,
            HttpServletRequest request) {
        var b = workflow.create(user(jwt), input);
        if (b.status() == Booking.Status.CONFIRMED)
            return ResponseEntity.status(201).body(b);
        if (b.status() == Booking.Status.PENDING)
            return ResponseEntity.accepted().body(b);
        if ("USER_CANCELLED".equals(b.failureCode()))
            return ResponseEntity.ok(b);
        int status = "ROOM_UNAVAILABLE".equals(b.failureCode()) ? 409 : 503;
        return ResponseEntity.status(status).body(new ErrorDto(java.time.Instant.now(), status,
                b.failureCode(), b.failureReason(), request.getRequestURI(), b.traceId(),
                java.util.Map.of("bookingId", b.id().toString(), "bookingStatus", "CANCELLED",
                        "compensationPending", Boolean.toString(b.compensationPending()))));
    }
    @GetMapping({"/bookings", "/api/bookings"})
    public PageDto<BookingView> history(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return transactions.history(user(jwt), page, size);
    }
    @GetMapping({"/booking/{id}", "/api/bookings/{id}"})
    public BookingView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return transactions.get(user(jwt), id);
    }
    @DeleteMapping({"/booking/{id}", "/api/bookings/{id}"})
    public BookingView cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return workflow.cancel(user(jwt), id);
    }
    @GetMapping({"/bookings/recommend", "/api/bookings/recommend"})
    public PageDto<RoomView> recommend(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long hotelId, @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        user(jwt);
        if (!endDate.isAfter(startDate))
            throw new ApiException(400, "INVALID_DATES", "endDate must be after startDate");
        try {
            return hotel.recommend(hotelId, startDate, endDate, page, size, jwt.getTokenValue());
        } catch (HotelUnavailableException e) {
            throw new ApiException(503, "HOTEL_UNAVAILABLE", e.getMessage());
        }
    }
    private long user(Jwt jwt) {
        try {
            long id = Long.parseLong(jwt.getSubject());
            users.requireActive(id);
            return id;
        } catch (NumberFormatException e) {
            throw new ApiException(401, "INVALID_SUBJECT", "Invalid token subject");
        }
    }
}
