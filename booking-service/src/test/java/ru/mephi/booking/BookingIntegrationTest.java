package ru.mephi.booking;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.*;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.GenericWebApplicationContext;
import ru.mephi.common.*;
import ru.mephi.hotel.*;
@SpringBootTest(classes = {BookingApplication.class, BookingIntegrationTest.HotelFixture.class},
        properties = {"demo.seed=false", "eureka.client.enabled=false",
                "spring.cloud.service-registry.auto-registration.enabled=false",
                "booking.recovery.enabled=false",
                "spring.datasource.url=jdbc:h2:mem:bookingIntegration;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"})
@AutoConfigureMockMvc
class BookingIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserService users;
    @Autowired UserRepository userRepository;
    @Autowired BookingRepository bookingRepository;
    @Autowired BookingTransactions transactions;
    @Autowired BookingWorkflow workflow;
    @Autowired MvcHotelPort hotel;
    @Autowired JwtTokens tokens;
    String alice, bob, admin;
    long aliceId;
    long roomId;
    LocalDate start = LocalDate.now().plusDays(30);
    LocalDate end = start.plusDays(2);
    @BeforeEach
    void givenUsersAndHotel() {
        bookingRepository.deleteAll();
        userRepository.deleteAll();
        hotel.context.getBean(AllocationRepository.class).deleteAll();
        hotel.context.getBean(RoomRepository.class).deleteAll();
        hotel.context.getBean(HotelRepository.class).deleteAll();
        var catalog = hotel.context.getBean(CatalogService.class);
        var h = catalog.createHotel(new CatalogDtos.HotelInput("Integration", "Moscow"));
        roomId = catalog.createRoom(new CatalogDtos.RoomInput(h.id(), "101", true)).id();
        var a = users.register(new UserDtos.Credentials("alice", "Alice123!"));
        alice = "Bearer " + a.accessToken();
        aliceId = a.user().id();
        bob = "Bearer " + users.register(new UserDtos.Credentials("bob", "Bob12345!")).accessToken();
        var adminUser = users.create(new UserDtos.CreateUser("admin", "Admin123!", User.Role.ADMIN));
        admin = "Bearer " + tokens.issue(adminUser.id().toString(), "ADMIN");
        hotel.reset();
    }
    BookingDtos.CreateBooking request(UUID key) {
        return new BookingDtos.CreateBooking(key, roomId, null, false, start, end);
    }
    ResultActions create(BookingDtos.CreateBooking input) throws Exception {
        return mvc.perform(post("/api/bookings")
                        .header("Authorization", alice)
                        .header("X-Trace-Id", "integration-trace")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(input)));
    }
    JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
    UUID id(JsonNode body) {
        return UUID.fromString(
                (body.has("id") ? body.get("id") : body.get("fields").get("bookingId")).asText());
    }
    @Test
    void successCommitsPendingBeforeHotelCallAndConfirms() throws Exception {
        hotel.onConfirm = bookingId
                -> assertThat(bookingRepository.findById(bookingId).orElseThrow().status)
                           .isEqualTo(Booking.Status.PENDING);
        JsonNode result = body(create(request(UUID.randomUUID()))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status").value("CONFIRMED")));
        assertThat(hotel.context.getBean(CatalogService.class).room(roomId).timesBooked()).isEqualTo(1);
        assertThat(bookingRepository.findById(id(result))).isPresent();
    }
    @Test
    void remoteFailureCancelsAndCompensates() throws Exception {
        hotel.failConfirm.set(true);
        JsonNode result = body(create(request(UUID.randomUUID()))
                        .andExpect(status().isServiceUnavailable())
                        .andExpect(jsonPath("$.fields.bookingStatus").value("CANCELLED"))
                        .andExpect(jsonPath("$.fields.compensationPending").value("false")));
        assertThat(hotel.releases).hasValue(1);
        assertThat(transactions.get(aliceId, id(result)).failureCode()).isEqualTo("HOTEL_UNAVAILABLE");
    }
    @Test
    void lostConfirmationResponseIsCompensatedAndRepeatedReleaseIsSafe() throws Exception {
        hotel.loseConfirmation.set(true);
        JsonNode result = body(create(request(UUID.randomUUID())).andExpect(status().isServiceUnavailable()));
        UUID bookingId = id(result);
        assertThat(hotel.context.getBean(CatalogService.class).room(roomId).timesBooked()).isZero();
        hotel.release(bookingId);
        assertThat(hotel.context.getBean(CatalogService.class).room(roomId).timesBooked()).isZero();
        assertThat(transactions.get(aliceId, bookingId).status()).isEqualTo(Booking.Status.CANCELLED);
    }
    @Test
    void failedCompensationRemainsDurableAndRecoveryRetriesIt() throws Exception {
        hotel.loseConfirmation.set(true);
        hotel.failRelease.set(true);
        UUID bookingId = id(body(create(request(UUID.randomUUID()))
                        .andExpect(status().isServiceUnavailable())
                        .andExpect(jsonPath("$.fields.compensationPending").value("true"))));
        assertThat(transactions.compensations()).contains(bookingId);
        assertThat(hotel.context.getBean(CatalogService.class).room(roomId).timesBooked()).isEqualTo(1);
        hotel.failRelease.set(false);
        new SagaRecovery(transactions, workflow).recover();
        assertThat(transactions.compensations()).doesNotContain(bookingId);
        assertThat(hotel.context.getBean(CatalogService.class).room(roomId).timesBooked()).isZero();
    }
    @Test
    void retryingSameRequestReturnsSameBookingAndMismatchedPayloadConflicts() throws Exception {
        UUID key = UUID.randomUUID();
        UUID bookingId = id(body(create(request(key)).andExpect(status().isCreated())));
        create(request(key))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(bookingId.toString()));
        assertThat(bookingRepository.count()).isEqualTo(1);
        create(new BookingDtos.CreateBooking(key, roomId, null, false, start, end.plusDays(1)))
                .andExpect(status().isConflict());
        assertThat(hotel.context.getBean(CatalogService.class).room(roomId).timesBooked()).isEqualTo(1);
    }
    @Test
    void ownershipAppliesToReadCancellationAndPaginatedHistory() throws Exception {
        UUID bookingId = id(body(create(request(UUID.randomUUID())).andExpect(status().isCreated())));
        mvc.perform(get("/api/bookings/{id}", bookingId).header("Authorization", bob))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/bookings/{id}", bookingId).header("Authorization", bob))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/bookings").header("Authorization", bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/bookings").header("Authorization", alice).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(delete("/api/bookings/{id}", bookingId).header("Authorization", alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(delete("/api/bookings/{id}", bookingId).header("Authorization", alice))
                .andExpect(status().isOk());
        assertThat(hotel.context.getBean(CatalogService.class).room(roomId).timesBooked()).isZero();
    }
    @Test
    void dateAndPagingValidationAndAnonymousAccess() throws Exception {
        mvc.perform(post("/booking")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(request(UUID.randomUUID()))))
                .andExpect(status().isUnauthorized());
        create(new BookingDtos.CreateBooking(UUID.randomUUID(), roomId, null, false, start, start))
                .andExpect(status().isBadRequest());
        create(new BookingDtos.CreateBooking(
                       UUID.randomUUID(), roomId, null, false, LocalDate.now().minusDays(1), end))
                .andExpect(status().isBadRequest());
        create(new BookingDtos.CreateBooking(UUID.randomUUID(), null, null, false, start, end))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/bookings").header("Authorization", alice).param("size", "101"))
                .andExpect(status().isBadRequest());
        assertThat(bookingRepository.count()).isZero();
    }
    @Test
    void registrationCannotEscalateRoleAndPasswordsAreHashed() throws Exception {
        mvc.perform(post("/user/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"mallory\",\"password\":\"Password123!\",\"role\":"
                                    + "\"ADMIN\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
        User saved = userRepository.findByUsername("mallory").orElseThrow();
        assertThat(saved.password).startsWith("$2").doesNotContain("Password123!");
        mvc.perform(post("/user/auth")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"mallory\",\"password\":\"Incorrect!\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/user/auth")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"mallory\",\"password\":\"Password123!\"}"))
                .andExpect(status().isOk());
    }
    @Test
    void adminUserCrudAndDisabledAccountCannotBookWithOldToken() throws Exception {
        mvc.perform(get("/user").header("Authorization", alice)).andExpect(status().isForbidden());
        mvc.perform(patch("/user/{id}", aliceId)
                            .header("Authorization", admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"alice_updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice_updated"));
        mvc.perform(delete("/user/{id}", aliceId).header("Authorization", admin))
                .andExpect(status().isNoContent());
        create(request(UUID.randomUUID())).andExpect(status().isUnauthorized());
    }
    @Test
    void recoveryProcessesCommittedPendingBooking() {
        var pending = transactions.create(aliceId, request(UUID.randomUUID()), "recovery-test");
        assertThat(pending.status()).isEqualTo(Booking.Status.PENDING);
        new SagaRecovery(transactions, workflow).recover();
        assertThat(transactions.get(aliceId, pending.id()).status()).isEqualTo(Booking.Status.CONFIRMED);
    }
    @Test
    void cancellationDuringInFlightConfirmationCannotResurrectBooking() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        AtomicReference<UUID> bookingId = new AtomicReference<>();
        hotel.onConfirm = id -> {
            bookingId.set(id);
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS))
                    throw new IllegalStateException("Test confirmation gate timed out");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        };
        var executor = Executors.newSingleThreadExecutor();
        try {
            var creation = executor.submit(() -> body(create(request(UUID.randomUUID()))));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            workflow.cancel(aliceId, bookingId.get());
            release.countDown();
            creation.get(5, TimeUnit.SECONDS);
            assertThat(transactions.get(aliceId, bookingId.get()).status())
                    .isEqualTo(Booking.Status.CANCELLED);
            assertThat(hotel.context.getBean(CatalogService.class).room(roomId).timesBooked()).isZero();
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
    @Test
    void parallelRedeliveryCreatesOneBookingAndOneAllocation() throws Exception {
        var input = request(UUID.randomUUID());
        var statuses = parallel(12, () -> create(input).andReturn().getResponse().getStatus());
        assertThat(statuses).allMatch(s -> s == 201 || s == 202);
        assertThat(bookingRepository.count()).isEqualTo(1);
        assertThat(hotel.context.getBean(CatalogService.class).room(roomId).timesBooked()).isEqualTo(1);
    }
    @Test
    void parallelDifferentRequestsHaveOneWinnerAndCompensateLosers() throws Exception {
        var statuses =
                parallel(12, () -> create(request(UUID.randomUUID())).andReturn().getResponse().getStatus());
        assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(11);
        assertThat(bookingRepository.findAll()
                           .stream()
                           .filter(b -> b.status == Booking.Status.CONFIRMED)
                           .count())
                .isEqualTo(1);
        assertThat(transactions.compensations()).isEmpty();
    }
    @Test
    void automaticSelectionIgnoresRoomIdAndBookingReturnsRecommendations() throws Exception {
        var catalog = hotel.context.getBean(CatalogService.class);
        long hotelId = catalog.room(roomId).hotelId();
        var second = catalog.createRoom(new CatalogDtos.RoomInput(hotelId, "102", true));
        create(request(UUID.randomUUID())).andExpect(status().isCreated());
        create(new BookingDtos.CreateBooking(UUID.randomUUID(), 999999L, hotelId, true, start, end))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").value(second.id()));
        mvc.perform(get("/api/bookings/recommend")
                            .header("Authorization", alice)
                            .param("startDate", end.toString())
                            .param("endDate", end.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(roomId));
    }

    @Test
    void expiredWorkerLeaseCannotOverwriteNewOwner() {
        var pending = transactions.create(aliceId, request(UUID.randomUUID()), "lease-test");
        var oldWork = transactions.claim(pending.id()).orElseThrow();
        var row = bookingRepository.findById(pending.id()).orElseThrow();
        row.leaseUntil = Instant.now().minusSeconds(1);
        bookingRepository.saveAndFlush(row);
        var newWork = transactions.claim(pending.id()).orElseThrow();
        assertThat(newWork.token()).isNotEqualTo(oldWork.token());
        var room = hotel.confirm(roomId, null, new SlotRequest(pending.id(), start, end));
        transactions.confirm(oldWork, room);
        assertThat(transactions.get(aliceId, pending.id()).status()).isEqualTo(Booking.Status.PENDING);
        transactions.confirm(newWork, room);
        assertThat(transactions.get(aliceId, pending.id()).status()).isEqualTo(Booking.Status.CONFIRMED);
    }

    @Test
    void historicalRequestReplayIsIdempotentAfterStartDateHasPassed() throws Exception {
        var input = new BookingDtos.CreateBooking(UUID.randomUUID(), roomId, null, false,
                LocalDate.now().minusDays(3), LocalDate.now().minusDays(1));
        var row = new Booking(userRepository.findById(aliceId).orElseThrow(), input, "historical-trace");
        row.status = Booking.Status.CONFIRMED;
        bookingRepository.saveAndFlush(row);
        create(input).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(row.id.toString()));
        assertThat(bookingRepository.count()).isEqualTo(1);
        assertThat(hotel.context.getBean(CatalogService.class).room(roomId).timesBooked()).isZero();
    }

    @Test
    void openApiDocumentsBookingAndErrorResponses() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/bookings'].post.responses['201'].content['application/"
                                    + "json'].schema['$ref']")
                                .value("#/components/schemas/BookingView"))
                .andExpect(jsonPath("$.paths['/api/bookings'].post.responses['503'].content['application/"
                                    + "json'].schema['$ref']")
                                .value("#/components/schemas/ErrorDto"));
    }
    private <T> List<T> parallel(int n, Callable<T> action) throws Exception {
        var executor = Executors.newFixedThreadPool(n);
        var gate = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++)
                futures.add(executor.submit(() -> {
                    gate.await();
                    return action.call();
                }));
            gate.countDown();
            List<T> results = new ArrayList<>();
            for (var f : futures)
                results.add(f.get(30, TimeUnit.SECONDS));
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
    @TestConfiguration
    static class HotelFixture {
        @Bean(destroyMethod = "close")
        @Primary
        MvcHotelPort testHotelPort(JwtTokens tokens, ObjectMapper json) {
            var context =
                    new SpringApplicationBuilder(HotelApplication.class)
                            .contextFactory(type -> {
                                var c = new GenericWebApplicationContext();
                                c.setServletContext(new MockServletContext());
                                return c;
                            })
                            .run("--server.port=0", "--demo.seed=false", "--eureka.client.enabled=false",
                                    "--spring.cloud.service-registry.auto-registration.enabled=false",
                                    "--spring.datasource.url=jdbc:h2:mem:separateHotel;DB_CLOSE_DELAY=-1;"
                                            + "LOCK_TIMEOUT=10000");
            return new MvcHotelPort(context, tokens, json);
        }
    }
    static class MvcHotelPort implements HotelPort, AutoCloseable {
        @Override
        public void close() {
            context.close();
        }
        final ConfigurableApplicationContext context;
        final MockMvc hotelMvc;
        final JwtTokens tokens;
        final ObjectMapper json;
        final AtomicBoolean failConfirm = new AtomicBoolean(), loseConfirmation = new AtomicBoolean(),
                            failRelease = new AtomicBoolean();
        final AtomicInteger releases = new AtomicInteger();
        volatile java.util.function.Consumer<UUID> onConfirm = id -> {};
        MvcHotelPort(ConfigurableApplicationContext context, JwtTokens tokens, ObjectMapper json) {
            this.context = context;
            this.tokens = tokens;
            this.json = json;
            hotelMvc = MockMvcBuilders
                               .webAppContextSetup(
                                       (org.springframework.web.context.WebApplicationContext) context)
                               .apply(springSecurity())
                               .build();
        }
        void reset() {
            failConfirm.set(false);
            loseConfirmation.set(false);
            failRelease.set(false);
            releases.set(0);
            onConfirm = id -> {};
        }
        @Override
        public RoomView confirm(Long roomId, Long hotelId, SlotRequest request) {
            onConfirm.accept(request.bookingId());
            if (failConfirm.get())
                throw new HotelUnavailableException(null);
            try {
                String path = roomId == null ? "/api/rooms/allocate"
                                             : "/api/rooms/" + roomId + "/confirm-availability";
                var builder = post(path)
                                      .header("Authorization", "Bearer " + tokens.internal())
                                      .contentType(MediaType.APPLICATION_JSON)
                                      .content(json.writeValueAsString(request));
                if (hotelId != null)
                    builder.param("hotelId", hotelId.toString());
                var response = hotelMvc.perform(builder).andReturn().getResponse();
                if (response.getStatus() == 409 || response.getStatus() == 404)
                    throw new HotelRejectedException();
                if (response.getStatus() != 200)
                    throw new AssertionError(response.getContentAsString());
                if (loseConfirmation.get())
                    throw new HotelUnavailableException(
                            new java.net.SocketTimeoutException("lost response after commit"));
                return json.readValue(response.getContentAsString(), RoomView.class);
            } catch (HotelRejectedException | HotelUnavailableException e) {
                throw e;
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
        @Override
        public void release(UUID id) {
            releases.incrementAndGet();
            if (failRelease.get())
                throw new HotelUnavailableException(null);
            try {
                hotelMvc.perform(post("/api/rooms/release")
                                         .header("Authorization", "Bearer " + tokens.internal())
                                         .contentType(MediaType.APPLICATION_JSON)
                                         .content(json.writeValueAsString(new ReleaseRequest(id))))
                        .andExpect(status().isNoContent());
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
        @Override
        public PageDto<RoomView> recommend(
                Long hotelId, LocalDate start, LocalDate end, int page, int size, String bearerToken) {
            try {
                var request = get("/api/rooms/recommend")
                                      .header("Authorization", "Bearer " + bearerToken)
                                      .param("startDate", start.toString())
                                      .param("endDate", end.toString())
                                      .param("page", Integer.toString(page))
                                      .param("size", Integer.toString(size));
                if (hotelId != null)
                    request.param("hotelId", hotelId.toString());
                String body = hotelMvc.perform(request)
                                      .andExpect(status().isOk())
                                      .andReturn()
                                      .getResponse()
                                      .getContentAsString();
                return json.readValue(
                        body, new com.fasterxml.jackson.core.type.TypeReference<PageDto<RoomView>>() {});
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
    }
}
