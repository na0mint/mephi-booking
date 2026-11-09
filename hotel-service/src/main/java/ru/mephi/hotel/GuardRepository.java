package ru.mephi.hotel;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import ru.mephi.common.ApiException;

public interface GuardRepository extends JpaRepository<AllocationGuard, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from AllocationGuard g where g.id=1")
    Optional<AllocationGuard> findLockedGuard();

    default void lockInventory() {
        if (findLockedGuard().isEmpty()) {
            throw new ApiException(503, "INVENTORY_INITIALIZING", "Hotel inventory is initializing");
        }
    }
}
