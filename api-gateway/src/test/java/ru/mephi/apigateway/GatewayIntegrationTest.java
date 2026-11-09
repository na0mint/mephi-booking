package ru.mephi.apigateway;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.*;
import org.springframework.cloud.gateway.route.*;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.*;
import ru.mephi.common.JwtTokens;
@SpringBootTest(classes = {GatewayApplication.class, GatewayIntegrationTest.ProbeRoutes.class},
        properties = {"eureka.client.enabled=false",
                "spring.cloud.service-registry.auto-registration.enabled=false",
                "spring.main.web-application-type=reactive"})
@AutoConfigureWebTestClient
class GatewayIntegrationTest {
    @Autowired WebTestClient web;
    @Autowired JwtTokens tokens;
    @Autowired GatewayRoutes routeConfiguration;
    @Autowired RouteLocatorBuilder routeBuilder;
    String bearer(String role) {
        return "Bearer " + tokens.issue("42", role);
    }
    @Test
    void anonymousAndMalformedTokensAreUnauthorized() {
        web.get()
                .uri("/api/bookings")
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectHeader()
                .exists("X-Trace-Id")
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("UNAUTHORIZED");
        web.get()
                .uri("/api/hotels")
                .header(HttpHeaders.AUTHORIZATION, "Bearer broken")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }
    @Test
    void userCannotAdministerOrReachInternalOperations() {
        web.post()
                .uri("/api/hotels")
                .header(HttpHeaders.AUTHORIZATION, bearer("USER"))
                .exchange()
                .expectStatus()
                .isForbidden();
        web.get()
                .uri("/user")
                .header(HttpHeaders.AUTHORIZATION, bearer("USER"))
                .exchange()
                .expectStatus()
                .isForbidden();
        for (String role : List.of("USER", "ADMIN", "INTERNAL"))
            for (String path : List.of("/api/rooms/1/release", "/api/rooms/1/confirm-availability",
                         "/api/rooms/release", "/api/rooms/allocate"))
                web.post()
                        .uri(path)
                        .header(HttpHeaders.AUTHORIZATION, bearer(role))
                        .exchange()
                        .expectStatus()
                        .isForbidden();
    }
    @Test
    void discoveryRoutesUseLogicalServiceNames() {
        var routes = routeConfiguration.routes(routeBuilder).getRoutes().collectList().block();
        assertThat(routes)
                .extracting(r -> r.getUri().toString())
                .contains("lb://booking-service", "lb://hotel-service");
    }
    @Test
    void jwtAndTraceAreForwardedToBackend() {
        String bearer = bearer("USER");
        web.get()
                .uri("/api/bookings/probe")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .header("X-Trace-Id", "gateway-trace-42")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals("X-Trace-Id", "gateway-trace-42")
                .expectBody()
                .jsonPath("$.authorization")
                .isEqualTo(bearer)
                .jsonPath("$.traceId")
                .isEqualTo("gateway-trace-42");
    }
    @TestConfiguration
    static class ProbeRoutes {
        @Bean
        RouteLocator probeRoute(RouteLocatorBuilder builder) {
            return builder.routes()
                    .route("test-probe", r -> r.order(-100).path("/api/bookings/probe").uri("forward:/probe"))
                    .build();
        }
        @Bean
        org.springframework.web.reactive.function.server
                .RouterFunction<org.springframework.web.reactive.function.server.ServerResponse>
                probeEndpoint() {
            return org.springframework.web.reactive.function.server.RouterFunctions.route(
                    org.springframework.web.reactive.function.server.RequestPredicates.GET("/probe"),
                    request
                    -> org.springframework.web.reactive.function.server.ServerResponse.ok().bodyValue(
                            Map.of("authorization", request.headers().firstHeader("Authorization"), "traceId",
                                    request.headers().firstHeader("X-Trace-Id"))));
        }
    }
}
