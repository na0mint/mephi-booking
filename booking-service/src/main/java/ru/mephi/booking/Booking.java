package ru.mephi.booking;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
@Entity
@Table(name = "bookings",
        uniqueConstraints =
                @UniqueConstraint(name = "uk_booking_request", columnNames = {"user_id", "request_id"}),
        indexes =
        {
            @Index(name = "idx_booking_history", columnList = "user_id,created_at")
            , @Index(name = "idx_booking_recovery", columnList = "status,lease_until"),
                    @Index(name = "idx_booking_compensation", columnList = "compensation_pending")
        })
public class Booking {
    public enum Status { PENDING, CONFIRMED, CANCELLED }
    @Id UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    User user;
    @Column(name = "request_id", nullable = false) UUID requestId;
    @Column(name = "room_id") Long roomId;
    Long requestedRoomId;
    Long hotelId;
    @Column(nullable = false) boolean autoSelect;
    @Column(name = "start_date", nullable = false) LocalDate startDate;
    @Column(name = "end_date", nullable = false) LocalDate endDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false) Status status;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(nullable = false) Instant updatedAt;
    @Column(name = "lease_until", nullable = false) Instant leaseUntil;
    UUID processToken;
    @Column(name = "compensation_pending", nullable = false) boolean compensationPending;
    String failureCode;
    @Column(length = 500) String failureReason;
    @Column(nullable = false, length = 64) String traceId;
    @Version long version;
    protected Booking() {
    }
    Booking(User user, BookingDtos.CreateBooking input, String traceId) {
        id = UUID.randomUUID();
        this.user = user;
        requestId = input.requestId();
        autoSelect = input.autoSelect();
        requestedRoomId = autoSelect ? null : input.roomId();
        roomId = requestedRoomId;
        hotelId = autoSelect ? input.hotelId() : null;
        startDate = input.startDate();
        endDate = input.endDate();
        status = Status.PENDING;
        createdAt = Instant.now();
        updatedAt = createdAt;
        leaseUntil = Instant.EPOCH;
        this.traceId = traceId;
    }
}
