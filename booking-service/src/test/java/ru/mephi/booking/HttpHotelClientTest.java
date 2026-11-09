package ru.mephi.booking;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.web.client.*;
import org.springframework.web.client.*;
import ru.mephi.common.*;
class HttpHotelClientTest {
    MockRestServiceServer server;
    HttpHotelClient client;
    SlotRequest slot =
            new SlotRequest(UUID.randomUUID(), LocalDate.now().plusDays(1), LocalDate.now().plusDays(2));
    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        JwtTokens tokens = new JwtTokens(null) {
            @Override
            public String internal() {
                return "service-token";
            }
        };
        client = new HttpHotelClient(builder, tokens, "http://hotel-service", 1, 1000);
    }
    @Test
    void retriesTransientServerFailuresThenSucceeds() {
        server.expect(ExpectedCount.times(2),
                      requestTo("http://hotel-service/api/rooms/1/confirm-availability"))
                .andRespond(withServerError());
        server.expect(requestTo("http://hotel-service/api/rooms/1/confirm-availability"))
                .andExpect(header("Authorization", "Bearer service-token"))
                .andExpect(jsonPath("$.bookingId").value(slot.bookingId().toString()))
                .andRespond(withSuccess(
                        "{\"id\":1,\"hotelId\":1,\"number\":\"101\",\"available\":true,\"timesBooked\":1}",
                        MediaType.APPLICATION_JSON));
        assertThat(client.confirm(1L, null, slot).id()).isEqualTo(1L);
        server.verify();
    }
    @Test
    void timeoutHasExactlyThreeAttemptsAndPreservesBookingId() {
        AtomicInteger attempts = new AtomicInteger();
        server.expect(ExpectedCount.times(3),
                      requestTo("http://hotel-service/api/rooms/1/confirm-availability"))
                .andExpect(jsonPath("$.bookingId").value(slot.bookingId().toString()))
                .andRespond(request -> {
                    attempts.incrementAndGet();
                    throw new SocketTimeoutException("read timeout");
                });
        assertThatThrownBy(() -> client.confirm(1L, null, slot))
                .isInstanceOf(HotelUnavailableException.class);
        assertThat(attempts).hasValue(3);
        server.verify();
    }
    @Test
    void doesNotRetryBusinessConflict() {
        server.expect(requestTo("http://hotel-service/api/rooms/1/confirm-availability"))
                .andRespond(withStatus(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> client.confirm(1L, null, slot)).isInstanceOf(HotelRejectedException.class);
        server.verify();
    }
    @Test
    void releaseHasIndependentCircuitAndRetries() {
        server.expect(ExpectedCount.times(2), requestTo("http://hotel-service/api/rooms/release"))
                .andRespond(withServerError());
        server.expect(requestTo("http://hotel-service/api/rooms/release"))
                .andExpect(jsonPath("$.bookingId").value(slot.bookingId().toString()))
                .andRespond(withNoContent());
        client.release(slot.bookingId());
        server.verify();
    }
}
