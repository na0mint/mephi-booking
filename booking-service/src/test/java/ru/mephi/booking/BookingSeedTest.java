package ru.mephi.booking;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
@SpringBootTest(properties = {"demo.seed=true", "eureka.client.enabled=false",
                        "spring.cloud.service-registry.auto-registration.enabled=false",
                        "booking.recovery.enabled=false",
                        "spring.datasource.url=jdbc:h2:mem:bookingSeed;DB_CLOSE_DELAY=-1"})
class BookingSeedTest {
    @Autowired UserRepository users;
    @Autowired BookingRepository bookings;
    @Autowired BookingTransactions transactions;
    @Test
    void seedsRolesAndMatchingBookingHistory() {
        assertThat(users.count()).isEqualTo(3);
        assertThat(users.findByUsername("admin").orElseThrow().role).isEqualTo(User.Role.ADMIN);
        assertThat(bookings.count()).isEqualTo(4);
        assertThat(bookings.findAll().stream().filter(b -> b.status == Booking.Status.CONFIRMED).count())
                .isEqualTo(3);
        long alice = users.findByUsername("alice").orElseThrow().id;
        assertThat(transactions.history(alice, 0, 20).totalElements()).isEqualTo(3);
        assertThat(transactions.compensations()).isEmpty();
    }
}
