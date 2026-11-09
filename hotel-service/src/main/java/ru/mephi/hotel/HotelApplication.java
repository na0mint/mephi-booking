package ru.mephi.hotel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import ru.mephi.common.*;
@SpringBootApplication
@Import({JwtConfiguration.class, ServletSecurity.class, ApiAdvice.class})
public class HotelApplication {
    public static void main(String[] args) {
        SpringApplication.run(HotelApplication.class, args);
    }
}
