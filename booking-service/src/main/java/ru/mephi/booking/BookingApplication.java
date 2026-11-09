package ru.mephi.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import ru.mephi.common.*;
@SpringBootApplication
@EnableScheduling
@Import({JwtConfiguration.class, ServletSecurity.class, ApiAdvice.class})
public class BookingApplication {
    public static void main(String[] args) {
        SpringApplication.run(BookingApplication.class, args);
    }
}
