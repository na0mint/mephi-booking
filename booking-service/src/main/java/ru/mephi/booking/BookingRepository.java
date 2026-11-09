package ru.mephi.booking;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
public interface BookingRepository extends JpaRepository<Booking, UUID> {
    Optional<Booking> findByUserIdAndRequestId(Long userId, UUID requestId);
    Page<Booking> findByUserId(Long userId, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id=:id")
    Optional<Booking> lockById(UUID id);
    List<Booking> findTop50ByStatusAndLeaseUntilLessThanEqualOrderByCreatedAt(
            Booking.Status status, Instant now);
    List<Booking> findTop50ByCompensationPendingTrueOrderByUpdatedAt();
}
