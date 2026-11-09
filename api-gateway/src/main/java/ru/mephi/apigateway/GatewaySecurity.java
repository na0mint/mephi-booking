package ru.mephi.apigateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import reactor.core.publisher.Mono;
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurity {
    @Bean
    ReactiveJwtDecoder reactiveJwtDecoder(JwtDecoder decoder) {
        return token -> Mono.fromCallable(() -> decoder.decode(token));
    }
    @Bean
    SecurityWebFilterChain security(ServerHttpSecurity http, GatewayErrors errors) {
        var roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(a
                        -> a.pathMatchers("/api/rooms/*/confirm-availability", "/api/rooms/*/release",
                                    "/api/rooms/release", "/api/rooms/allocate")
                                .denyAll()
                                .pathMatchers("/actuator/health", "/actuator/info", "/docs/**")
                                .permitAll()
                                .pathMatchers(HttpMethod.POST, "/user/register", "/user/auth")
                                .permitAll()
                                .pathMatchers("/user", "/user/**")
                                .hasRole("ADMIN")
                                .pathMatchers(
                                        HttpMethod.GET, "/api/rooms/statistics", "/api/hotels/statistics")
                                .hasRole("ADMIN")
                                .pathMatchers(HttpMethod.GET, "/api/hotels/**", "/api/rooms/**")
                                .hasAnyRole("USER", "ADMIN")
                                .pathMatchers("/api/hotels/**", "/api/rooms/**")
                                .hasRole("ADMIN")
                                .pathMatchers("/api/bookings/**", "/booking/**", "/bookings/**")
                                .hasAnyRole("USER", "ADMIN")
                                .anyExchange()
                                .denyAll())
                .oauth2ResourceServer(o
                        -> o.jwt(j
                                    -> j.jwtAuthenticationConverter(
                                            new ReactiveJwtAuthenticationConverterAdapter(converter)))
                                .authenticationEntryPoint(
                                        (e, x)
                                                -> errors.write(e, 401, "UNAUTHORIZED",
                                                        "A valid bearer token is required")))
                .exceptionHandling(e
                        -> e.authenticationEntryPoint((x, c)
                                                              -> errors.write(x, 401, "UNAUTHORIZED",
                                                                      "A valid bearer token is required"))
                                .accessDeniedHandler((x, c)
                                                             -> errors.write(x, 403, "FORBIDDEN",
                                                                     "Insufficient permissions")))
                .build();
    }
}
