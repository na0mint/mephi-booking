package ru.mephi.hotel;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.mephi.common.*;
@SpringBootTest(properties = {"demo.seed=false", "eureka.client.enabled=false",
                        "spring.cloud.service-registry.auto-registration.enabled=false",
                        "spring.datasource.url=jdbc:h2:mem:hotelTests;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"})
@AutoConfigureMockMvc
class HotelIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokens tokens;
    @Autowired CatalogService catalog;
    @Autowired AllocationService allocations;
    @Autowired AllocationRepository allocationRepository;
    @Autowired RoomRepository roomRepository;
    @Autowired HotelRepository hotelRepository;
    Long hotelId;
    List<RoomView> rooms;
    LocalDate start = LocalDate.now().plusDays(10);
    LocalDate end = start.plusDays(3);
    @BeforeEach
    void givenFourRooms() {
        allocationRepository.deleteAll();
        roomRepository.deleteAll();
        hotelRepository.deleteAll();
        hotelId = catalog.createHotel(new CatalogDtos.HotelInput("Campus", "Moscow")).id();
        rooms = new ArrayList<>();
        for (int i = 1; i <= 4; i++)
            rooms.add(catalog.createRoom(new CatalogDtos.RoomInput(hotelId, "10" + i, true)));
    }
    String bearer(String role) {
        return "Bearer " + tokens.issue("42", role);
    }
    SlotRequest slot(UUID id) {
        return new SlotRequest(id, start, end);
    }
    @Test
    void catalogRequiresJwtAndAdminForWrites() throws Exception {
        mvc.perform(get("/api/hotels"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(header().exists("X-Trace-Id"));
        mvc.perform(post("/api/hotels")
                            .header("Authorization", bearer("USER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Other\",\"address\":\"Address\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/hotels")
                            .header("Authorization", bearer("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Other\",\"address\":\"Address\"}"))
                .andExpect(status().isCreated());
    }
    @Test
    void malformedJwtIsRejectedAndInternalRoleCannotReadCatalog() throws Exception {
        mvc.perform(get("/api/hotels").header("Authorization", "Bearer corrupted"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/hotels").header("Authorization", bearer("INTERNAL")))
                .andExpect(status().isForbidden());
    }
    @Test
    void validationHasUniformErrorAndTrace() throws Exception {
        mvc.perform(post("/api/hotels")
                            .header("Authorization", bearer("ADMIN"))
                            .header("X-Trace-Id", "catalog-validation")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"\",\"address\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").exists())
                .andExpect(jsonPath("$.traceId").value("catalog-validation"));
        mvc.perform(get("/api/rooms").header("Authorization", bearer("USER")).param("page", "-1"))
                .andExpect(status().isBadRequest());
    }
    @Test
    void hotelAndRoomCrudAndUniqueness() throws Exception {
        mvc.perform(put("/api/hotels/{id}", hotelId)
                            .header("Authorization", bearer("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Updated\",\"address\":\"New address\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"));
        mvc.perform(get("/api/hotels")
                            .header("Authorization", bearer("USER"))
                            .param("name", "upd")
                            .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(
                   post("/api/rooms")
                           .header("Authorization", bearer("ADMIN"))
                           .contentType(MediaType.APPLICATION_JSON)
                           .content(json.writeValueAsString(new CatalogDtos.RoomInput(hotelId, "101", true))))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/hotels/{id}", hotelId).header("Authorization", bearer("ADMIN")))
                .andExpect(status().isConflict());
        for (RoomView r : rooms)
            mvc.perform(delete("/api/rooms/{id}", r.id()).header("Authorization", bearer("ADMIN")))
                    .andExpect(status().isNoContent());
        mvc.perform(delete("/api/hotels/{id}", hotelId).header("Authorization", bearer("ADMIN")))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/hotels/{id}", hotelId).header("Authorization", bearer("USER")))
                .andExpect(status().isNotFound());
    }
    @Test
    void recommendationsUseCountersThenIdsAndExcludeOccupiedAndMaintenance() throws Exception {
        allocations.confirm(rooms.get(0).id(), null,
                new SlotRequest(UUID.randomUUID(), start.plusDays(20), end.plusDays(20)));
        allocations.confirm(rooms.get(1).id(), null, slot(UUID.randomUUID()));
        catalog.updateRoom(rooms.get(3).id(), new CatalogDtos.RoomInput(hotelId, "104", false));
        mvc.perform(get("/api/rooms/recommend")
                            .header("Authorization", bearer("USER"))
                            .param("startDate", start.toString())
                            .param("endDate", end.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(rooms.get(2).id()))
                .andExpect(jsonPath("$.content[1].id").value(rooms.get(0).id()));
    }
    @Test
    void halfOpenDateIntervalsAllowCheckoutDayReuse() {
        Long room = rooms.get(0).id();
        allocations.confirm(room, null, slot(UUID.randomUUID()));
        assertThatThrownBy(
                ()
                        -> allocations.confirm(room, null,
                                new SlotRequest(UUID.randomUUID(), start.plusDays(1), end.plusDays(1))))
                .isInstanceOf(ApiException.class);
        assertThat(allocations.confirm(room, null, new SlotRequest(UUID.randomUUID(), end, end.plusDays(1)))
                           .id())
                .isEqualTo(room);
    }
    @Test
    void releaseIsIdempotentAndTombstonePreventsLateConfirmation() {
        Long room = rooms.get(0).id();
        UUID id = UUID.randomUUID();
        allocations.confirm(room, null, slot(id));
        allocations.release(room, id);
        allocations.release(room, id);
        assertThat(catalog.room(room).timesBooked()).isZero();
        assertThatThrownBy(() -> allocations.confirm(room, null, slot(id))).isInstanceOf(ApiException.class);
        UUID late = UUID.randomUUID();
        allocations.release(null, late);
        assertThatThrownBy(() -> allocations.confirm(null, hotelId, slot(late)))
                .isInstanceOf(ApiException.class);
    }
    @Test
    void internalEndpointsDenyUserAndAdminAndAcceptServiceToken() throws Exception {
        String body = json.writeValueAsString(slot(UUID.randomUUID()));
        for (String role : List.of("USER", "ADMIN"))
            mvc.perform(post("/api/rooms/{id}/confirm-availability", rooms.get(0).id())
                                .header("Authorization", bearer(role))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                    .andExpect(status().isForbidden());
        mvc.perform(post("/api/rooms/{id}/confirm-availability", rooms.get(0).id())
                            .header("Authorization", "Bearer " + tokens.internal())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                .andExpect(status().isOk());
    }
    @Test
    void parallelExplicitReservationsHaveExactlyOneWinner() throws Exception {
        var results = parallel(24, () -> {
            try {
                allocations.confirm(rooms.get(0).id(), null, slot(UUID.randomUUID()));
                return true;
            } catch (ApiException e) {
                assertThat(e.status()).isEqualTo(409);
                return false;
            }
        });
        assertThat(results.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
        assertThat(catalog.room(rooms.get(0).id()).timesBooked()).isEqualTo(1);
    }
    @Test
    void parallelDuplicateDeliveryDoesNotIncrementCounterTwice() throws Exception {
        UUID id = UUID.randomUUID();
        parallel(24, () -> allocations.confirm(rooms.get(0).id(), null, slot(id)));
        assertThat(allocationRepository.count()).isEqualTo(1);
        assertThat(catalog.room(rooms.get(0).id()).timesBooked()).isEqualTo(1);
    }
    @Test
    void parallelAutomaticAllocationFillsEachRoomExactlyOnce() throws Exception {
        var results = parallel(24, () -> {
            try {
                return allocations.confirm(null, hotelId, slot(UUID.randomUUID())).id();
            } catch (ApiException e) {
                return -1L;
            }
        });
        assertThat(results.stream().filter(id -> id != -1L).distinct().count()).isEqualTo(4);
        assertThat(results.stream().filter(id -> id != -1L).count()).isEqualTo(4);
    }
    @Test
    void parallelNonOverlappingAutomaticBookingsStayEvenlyDistributed() throws Exception {
        var counter = new java.util.concurrent.atomic.AtomicInteger();
        parallel(40, () -> {
            int day = counter.getAndIncrement() * 4;
            return allocations.confirm(null, hotelId,
                    new SlotRequest(UUID.randomUUID(), start.plusDays(day), end.plusDays(day)));
        });
        var counts = rooms.stream().map(r -> catalog.room(r.id()).timesBooked()).toList();
        assertThat(counts).containsOnly(10L);
    }
    @Test
    void statisticsClipOccupiedNightsToRequestedPeriod() throws Exception {
        allocations.confirm(rooms.get(0).id(), null, slot(UUID.randomUUID()));
        mvc.perform(get("/api/rooms/statistics")
                            .header("Authorization", bearer("ADMIN"))
                            .param("startDate", start.plusDays(1).toString())
                            .param("endDate", end.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOccupiedNights").value(2))
                .andExpect(jsonPath("$.totalRooms").value(4));
        mvc.perform(get("/api/rooms/statistics")
                            .header("Authorization", bearer("USER"))
                            .param("startDate", start.toString())
                            .param("endDate", end.toString()))
                .andExpect(status().isForbidden());
    }
    @Test
    void dateValidationAndOpenApiAreAvailable() throws Exception {
        mvc.perform(get("/api/rooms")
                            .header("Authorization", bearer("USER"))
                            .param("startDate", start.toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth").exists());
    }

    @Autowired org.springframework.security.oauth2.jwt.JwtEncoder encoder;
    @Autowired org.springframework.security.oauth2.jwt.JwtDecoder decoder;
    @Test
    void rejectsExpiredWrongIssuerAndTamperedTokens() throws Exception {
        var now = java.time.Instant.now();
        for (boolean expired : new boolean[] {true, false}) {
            var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                                 .issuer(expired ? "mephi-booking" : "other-issuer")
                                 .subject("42")
                                 .issuedAt(now.minusSeconds(7200))
                                 .expiresAt(expired ? now.minusSeconds(120) : now.plusSeconds(3600))
                                 .claim("roles", List.of("USER"))
                                 .build();
            String token = encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                                                  org.springframework.security.oauth2.jwt.JwsHeader
                                                          .with(org.springframework.security.oauth2.jose.jws
                                                                          .MacAlgorithm.HS256)
                                                          .build(),
                                                  claims))
                                   .getTokenValue();
            mvc.perform(get("/api/hotels").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
        var token = tokens.issue("42", "USER");
        int signature = token.lastIndexOf('.') + 1;
        String tampered = token.substring(0, signature) + (token.charAt(signature) == 'A' ? "B" : "A")
                + token.substring(signature + 1);
        mvc.perform(get("/api/hotels").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
        var jwt = decoder.decode(token);
        assertThat(java.time.Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).getSeconds())
                .isEqualTo(3600);
    }
    @Test
    void unknownRoutesAndUnsupportedMethodsKeepCorrectErrorStatus() throws Exception {
        mvc.perform(get("/missing-route").header("Authorization", bearer("USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(patch("/api/hotels").header("Authorization", bearer("ADMIN")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }
    private <T> List<T> parallel(int count, Callable<T> action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        var startGate = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++)
                futures.add(executor.submit(() -> {
                    startGate.await();
                    return action.call();
                }));
            startGate.countDown();
            List<T> results = new ArrayList<>();
            for (var f : futures)
                results.add(f.get(30, TimeUnit.SECONDS));
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
