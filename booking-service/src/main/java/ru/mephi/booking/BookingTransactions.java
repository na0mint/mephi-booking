package ru.mephi.booking;

import static ru.mephi.booking.BookingDtos.*;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.common.*;
@Service
public class BookingTransactions {
    private static final Logger log = LoggerFactory.getLogger(BookingTransactions.class);
    private final BookingRepository bookings;
    private final UserRepository users;
    private final long leaseSeconds;
    public BookingTransactions(BookingRepository bookings, UserRepository users,
            @Value("${booking.lease-seconds:30}") long leaseSeconds) {
        this.bookings = bookings;
        this.users = users;
        this.leaseSeconds = leaseSeconds;
    }
    @Transactional
    public BookingView create(long userId, CreateBooking input, String traceId) {
        // Serialize request-key creation per user; the unique index remains the last line of defense.
        User user = users.lockById(userId)
                            .filter(u -> u.active)
                            .orElseThrow(() -> new ApiException(401, "USER_DISABLED", "User unavailable"));
        var old = bookings.findByUserIdAndRequestId(userId, input.requestId());
        if (old.isPresent()) {
            Booking b = old.get();
            if (b.autoSelect != input.autoSelect()
                    || !Objects.equals(b.requestedRoomId, input.autoSelect() ? null : input.roomId())
                    || !Objects.equals(b.hotelId, input.autoSelect() ? input.hotelId() : null)
                    || !b.startDate.equals(input.startDate()) || !b.endDate.equals(input.endDate()))
                throw ApiException.conflict("requestId reused with a different payload");
            return BookingView.from(b);
        }
        if (input.startDate().isBefore(java.time.LocalDate.now())) {
            throw new ApiException(400, "INVALID_DATES", "startDate must not be in the past");
        }
        Booking b = bookings.saveAndFlush(new Booking(user, input, traceId));
        log.info("event=booking_pending bookingId={} requestId={}", b.id, b.requestId);
        return BookingView.from(b);
    }
    @Transactional
    public Optional<Work> claim(UUID id) {
        Booking b = requireLocked(id);
        Instant now = Instant.now();
        if (b.status != Booking.Status.PENDING || b.leaseUntil.isAfter(now))
            return Optional.empty();
        b.processToken = UUID.randomUUID();
        b.leaseUntil = now.plusSeconds(leaseSeconds);
        b.updatedAt = now;
        return Optional.of(new Work(b.id, b.processToken, b.requestedRoomId, b.hotelId, b.autoSelect,
                b.startDate, b.endDate, b.traceId));
    }
    @Transactional
    public void confirm(Work work, RoomView room) {
        Booking b = requireLocked(work.id());
        if (!owns(b, work))
            return;
        b.roomId = room.id();
        b.status = Booking.Status.CONFIRMED;
        b.updatedAt = Instant.now();
        b.processToken = null;
        log.info("event=booking_confirmed bookingId={} roomId={}", b.id, b.roomId);
    }
    @Transactional
    public void fail(Work work, String code, String reason) {
        Booking b = requireLocked(work.id());
        if (!owns(b, work))
            return;
        markCancelled(b, code, reason);
    }
    @Transactional
    public BookingView cancel(long userId, UUID id) {
        Booking b = requireLocked(id);
        checkOwner(b, userId);
        if (b.status != Booking.Status.CANCELLED)
            markCancelled(b, "USER_CANCELLED", "Cancelled by user");
        return BookingView.from(b);
    }
    @Transactional
    public void compensated(UUID id) {
        Booking b = requireLocked(id);
        if (b.status == Booking.Status.CANCELLED) {
            b.compensationPending = false;
            b.updatedAt = Instant.now();
            log.info("event=compensation_completed bookingId={}", id);
        }
    }
    @Transactional(readOnly = true)
    public BookingView get(long userId, UUID id) {
        Booking b = bookings.findById(id).orElseThrow(() -> ApiException.notFound("Booking"));
        checkOwner(b, userId);
        return BookingView.from(b);
    }
    @Transactional(readOnly = true)
    public BookingView getInternal(UUID id) {
        return BookingView.from(bookings.findById(id).orElseThrow(() -> ApiException.notFound("Booking")));
    }
    @Transactional(readOnly = true)
    public PageDto<BookingView> history(long userId, int page, int size) {
        return PageDto.from(
                bookings.findByUserId(userId,
                                PageRequest.of(page, size,
                                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
                        .map(BookingView::from));
    }
    @Transactional(readOnly = true)
    public java.util.List<UUID> recoverable() {
        return bookings
                .findTop50ByStatusAndLeaseUntilLessThanEqualOrderByCreatedAt(
                        Booking.Status.PENDING, Instant.now())
                .stream()
                .map(b -> b.id)
                .toList();
    }
    @Transactional(readOnly = true)
    public java.util.List<UUID> compensations() {
        return bookings.findTop50ByCompensationPendingTrueOrderByUpdatedAt().stream().map(b -> b.id).toList();
    }
    private Booking requireLocked(UUID id) {
        return bookings.lockById(id).orElseThrow(() -> ApiException.notFound("Booking"));
    }
    private boolean owns(Booking b, Work work) {
        return b.status == Booking.Status.PENDING && Objects.equals(b.processToken, work.token());
    }
    private void checkOwner(Booking b, long userId) {
        if (b.user.getId() != userId)
            throw ApiException.notFound("Booking");
    }
    private void markCancelled(Booking b, String code, String reason) {
        b.status = Booking.Status.CANCELLED;
        b.compensationPending = true;
        b.failureCode = code;
        b.failureReason = reason;
        b.processToken = null;
        b.updatedAt = Instant.now();
        log.info("event=booking_cancelled bookingId={} reason={} compensationPending=true", b.id, code);
    }
}
