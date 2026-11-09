package ru.mephi.apigateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;
import ru.mephi.common.ErrorDto;
@Component
@Order(-2)
public class GatewayErrors implements ErrorWebExceptionHandler {
    private final ObjectMapper mapper;
    public GatewayErrors(ObjectMapper mapper) {
        this.mapper = mapper;
    }
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
        if (exchange.getResponse().isCommitted())
            return Mono.error(error);
        int status = error instanceof ResponseStatusException e ? e.getStatusCode().value() : 502;
        return write(exchange, status, status == 503 ? "SERVICE_UNAVAILABLE" : "GATEWAY_ERROR",
                "Gateway could not complete the request");
    }
    Mono<Void> write(ServerWebExchange exchange, int status, String code, String message) {
        exchange.getResponse().setStatusCode(HttpStatusCode.valueOf(status));
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (status == 401)
            exchange.getResponse().getHeaders().set("WWW-Authenticate", "Bearer");
        try {
            byte[] body = mapper.writeValueAsBytes(new ErrorDto(Instant.now(), status, code, message,
                    exchange.getRequest().getPath().value(), exchange.getAttribute("traceId"), Map.of()));
            return exchange.getResponse().writeWith(
                    Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }
}
