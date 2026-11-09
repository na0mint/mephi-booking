package ru.mephi.booking;

import static ru.mephi.booking.BookingDtos.*;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import ru.mephi.common.*;
@Service
public class BookingWorkflow {
    private static final Logger log = LoggerFactory.getLogger(BookingWorkflow.class);
    private final BookingTransactions transactions;
    private final HotelPort hotel;
    public BookingWorkflow(BookingTransactions transactions, HotelPort hotel) {
        this.transactions = transactions;
        this.hotel = hotel;
    }
    public BookingView create(long userId, CreateBooking input) {
        input.validate();
        var b = transactions.create(userId, input, TraceIds.safe(MDC.get("traceId")));
        process(b.id());
        return transactions.get(userId, b.id());
    }
    public void process(UUID id) {
        var claimed = transactions.claim(id);
        if (claimed.isEmpty())
            return;
        Work work = claimed.get();
        try (var trace = TraceContext.open(work.traceId(), id.toString())) {
            log.info("event=booking_processing bookingId={}", id);
            try {
                RoomView room = hotel.confirm(work.autoSelect() ? null : work.roomId(), work.hotelId(),
                        new SlotRequest(id, work.startDate(), work.endDate()));
                transactions.confirm(work, room);
            } catch (HotelRejectedException e) {
                transactions.fail(work, "ROOM_UNAVAILABLE", e.getMessage());
            } catch (RuntimeException e) {
                log.warn("event=hotel_confirmation_failed bookingId={} reason={}", id,
                        e.getClass().getSimpleName());
                transactions.fail(work, "HOTEL_UNAVAILABLE", "Hotel confirmation failed or timed out");
            }
            compensate(id);
        }
    }
    public BookingView cancel(long userId, UUID id) {
        transactions.cancel(userId, id);
        compensate(id);
        return transactions.get(userId, id);
    }
    public void compensate(UUID id) {
        var b = transactions.getInternal(id);
        if (!b.compensationPending())
            return;
        try (var trace = TraceContext.open(b.traceId(), id.toString())) {
            try {
                hotel.release(id);
                transactions.compensated(id);
            } catch (RuntimeException e) {
                log.warn("event=compensation_deferred bookingId={} reason={}", id,
                        e.getClass().getSimpleName());
            }
        }
    }
}
