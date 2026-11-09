package ru.mephi.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
@EnableMethodSecurity
public class ServletSecurity {
    @Bean
    FilterRegistrationBean<TraceFilter> traceFilter() {
        var bean = new FilterRegistrationBean<>(new TraceFilter());
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper) throws Exception {
        var roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        http.csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a
                        -> a.requestMatchers("/user/register", "/user/auth", "/v3/api-docs/**",
                                    "/swagger-ui/**", "/swagger-ui.html", "/actuator/health",
                                    "/actuator/info")
                                .permitAll()
                                .anyRequest()
                                .authenticated())
                .oauth2ResourceServer(o
                        -> o.jwt(j -> j.jwtAuthenticationConverter(converter))
                                .authenticationEntryPoint((r, s, e) -> {
                                    s.setStatus(401);
                                    s.setContentType("application/json");
                                    s.setHeader("WWW-Authenticate", "Bearer");
                                    mapper.writeValue(s.getOutputStream(),
                                            new ErrorDto(Instant.now(), 401, "UNAUTHORIZED",
                                                    "A valid bearer token is required", r.getRequestURI(),
                                                    MDC.get("traceId"), Map.of()));
                                }))
                .exceptionHandling(e -> e.accessDeniedHandler((r, s, x) -> {
                    s.setStatus(403);
                    s.setContentType("application/json");
                    mapper.writeValue(s.getOutputStream(),
                            new ErrorDto(Instant.now(), 403, "FORBIDDEN", "Insufficient permissions",
                                    r.getRequestURI(), MDC.get("traceId"), Map.of()));
                }));
        return http.build();
    }
}
