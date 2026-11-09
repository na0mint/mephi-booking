package ru.mephi.booking;

import java.time.*;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import ru.mephi.common.*;
@Component
public class HttpHotelClient implements HotelPort {
    private static final Logger log = LoggerFactory.getLogger(HttpHotelClient.class);
    private final RestClient client;
    private final JwtTokens tokens;
    private final RetryTemplate retry;
    private final HotelCircuitBreaker confirmationBreaker;
    private final HotelCircuitBreaker releaseBreaker;
    public HttpHotelClient(@LoadBalanced RestClient.Builder builder, JwtTokens tokens,
            @Value("${hotel.base-url:http://hotel-service}") String baseUrl,
            @Value("${hotel.retry-delay-ms:150}") long retryDelay,
            @Value("${hotel.breaker-wait-ms:5000}") long breakerWait) {
        this.client = builder.baseUrl(baseUrl).build();
        this.tokens = tokens;
        retry = RetryTemplate.builder()
                        .maxAttempts(3)
                        .exponentialBackoff(retryDelay, 2, retryDelay * 4)
                        .retryOn(HotelUnavailableException.class)
                        .build();
        retry.registerListener(new RetryListener() {
            @Override
            public <T, E extends Throwable> void onError(
                    RetryContext context, RetryCallback<T, E> callback, Throwable error) {
                log.warn("event=hotel_attempt_failed attempt={} reason={}", context.getRetryCount(),
                        error.getClass().getSimpleName());
            }
        });
        confirmationBreaker = new HotelCircuitBreaker("hotel-confirm", Duration.ofMillis(breakerWait));
        releaseBreaker = new HotelCircuitBreaker("hotel-release", Duration.ofMillis(breakerWait));
    }
    @Override
    public RoomView confirm(Long roomId, Long hotelId, SlotRequest request) {
        return execute(confirmationBreaker,
                ()
                        -> client.post()
                                .uri(b -> {
                                    if (roomId != null)
                                        return b.path("/api/rooms/{id}/confirm-availability").build(roomId);
                                    b.path("/api/rooms/allocate");
                                    if (hotelId != null)
                                        b.queryParam("hotelId", hotelId);
                                    return b.build();
                                })
                                .headers(this::headers)
                                .body(request)
                                .retrieve()
                                .body(RoomView.class));
    }
    @Override
    public void release(UUID bookingId) {
        execute(releaseBreaker, () -> {
            client.post()
                    .uri("/api/rooms/release")
                    .headers(this::headers)
                    .body(new ReleaseRequest(bookingId))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        });
    }
    @Override
    public PageDto<RoomView> recommend(
            Long hotelId, LocalDate start, LocalDate end, int page, int size, String bearerToken) {
        return execute(confirmationBreaker,
                ()
                        -> client.get()
                                .uri(b -> {
                                    b.path("/api/rooms/recommend")
                                            .queryParam("startDate", start)
                                            .queryParam("endDate", end)
                                            .queryParam("page", page)
                                            .queryParam("size", size);
                                    if (hotelId != null)
                                        b.queryParam("hotelId", hotelId);
                                    return b.build();
                                })
                                .headers(h -> {
                                    h.setBearerAuth(bearerToken);
                                    h.set("X-Trace-Id", TraceIds.safe(MDC.get("traceId")));
                                })
                                .retrieve()
                                .body(new ParameterizedTypeReference<PageDto<RoomView>>() {}));
    }
    private void headers(HttpHeaders headers) {
        headers.setBearerAuth(tokens.internal());
        headers.set("X-Trace-Id", TraceIds.safe(MDC.get("traceId")));
    }
    private <T> T execute(HotelCircuitBreaker breaker, Supplier<T> call) {
        return breaker.execute(() -> retry.execute(context -> {
            try {
                T result = call.get();
                if (result == null)
                    throw new HotelUnavailableException(null);
                return result;
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 409 || e.getStatusCode().value() == 404)
                    throw new HotelRejectedException();
                throw new HotelUnavailableException(e);
            } catch (RestClientException e) {
                throw new HotelUnavailableException(e);
            }
        }));
    }
}
