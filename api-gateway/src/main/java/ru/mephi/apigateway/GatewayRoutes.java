package ru.mephi.apigateway;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class GatewayRoutes {
    @Bean
    RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("booking",
                        r
                        -> r.path("/api/bookings/**", "/booking/**", "/bookings/**", "/user/**")
                                .uri("lb://booking-service"))
                .route("hotel", r -> r.path("/api/hotels/**", "/api/rooms/**").uri("lb://hotel-service"))
                .route("booking-docs",
                        r
                        -> r.path("/docs/booking")
                                .filters(f -> f.setPath("/v3/api-docs"))
                                .uri("lb://booking-service"))
                .route("hotel-docs",
                        r
                        -> r.path("/docs/hotel")
                                .filters(f -> f.setPath("/v3/api-docs"))
                                .uri("lb://hotel-service"))
                .build();
    }
}
