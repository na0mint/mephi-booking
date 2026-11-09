package ru.mephi.hotel;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
@Entity
@Table(name = "room_allocations",
        indexes = @Index(name = "idx_allocation_overlap", columnList = "room_id,state,start_date,end_date"))
public class Allocation {
    public enum State { HELD, RELEASED }
    @Id @Column(name = "booking_id") UUID bookingId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "room_id") Room room;
    @Column(name = "start_date") LocalDate startDate;
    @Column(name = "end_date") LocalDate endDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false) State state;
    @Column(nullable = false) Instant updatedAt;
    protected Allocation() {
    }
    static Allocation held(Room room, ru.mephi.common.SlotRequest request) {
        Allocation a = new Allocation();
        a.bookingId = request.bookingId();
        a.room = room;
        a.startDate = request.startDate();
        a.endDate = request.endDate();
        a.state = State.HELD;
        a.updatedAt = Instant.now();
        return a;
    }
    static Allocation tombstone(UUID id) {
        Allocation a = new Allocation();
        a.bookingId = id;
        a.state = State.RELEASED;
        a.updatedAt = Instant.now();
        return a;
    }
}
