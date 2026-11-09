package ru.mephi.booking;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id=:id")
    Optional<User> lockById(Long id);
    Page<User> findByActiveTrue(Pageable pageable);
}
