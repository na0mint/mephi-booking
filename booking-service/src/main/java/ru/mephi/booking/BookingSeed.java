package ru.mephi.booking;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.common.DemoData;
@Component
public class BookingSeed implements ApplicationRunner {
    private final UserRepository users;
    private final BookingRepository bookings;
    private final PasswordEncoder passwords;
    private final boolean demo;
    public BookingSeed(UserRepository users, BookingRepository bookings, PasswordEncoder passwords,
            @Value("${demo.seed:true}") boolean demo) {
        this.users = users;
        this.bookings = bookings;
        this.passwords = passwords;
        this.demo = demo;
    }
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!demo || users.count() > 0)
            return;
        users.save(new User("admin", passwords.encode("Admin123!"), User.Role.ADMIN));
        users.save(new User("alice", passwords.encode("Alice123!"), User.Role.USER));
        users.save(new User("bob", passwords.encode("Bob12345!"), User.Role.USER));
        users.flush();
        for (var sample : DemoData.reservations()) {
            var user = users.findByUsername(sample.username()).orElseThrow();
            var request = new BookingDtos.CreateBooking(
                    sample.requestId(), sample.roomId(), null, false, sample.startDate(), sample.endDate());
            var booking = new Booking(user, request, "demo-seed");
            booking.id = sample.bookingId();
            booking.status = sample.cancelled() ? Booking.Status.CANCELLED : Booking.Status.CONFIRMED;
            if (sample.cancelled()) {
                booking.failureCode = "USER_CANCELLED";
                booking.failureReason = "Demonstration cancellation";
            }
            bookings.save(booking);
        }
    }
}
