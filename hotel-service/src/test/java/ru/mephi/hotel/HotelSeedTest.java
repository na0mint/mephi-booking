package ru.mephi.hotel;

import static org.assertj.core.api.Assertions.*;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
@SpringBootTest(properties = {"demo.seed=true", "eureka.client.enabled=false",
                        "spring.cloud.service-registry.auto-registration.enabled=false",
                        "spring.datasource.url=jdbc:h2:mem:hotelSeed;DB_CLOSE_DELAY=-1"})
class HotelSeedTest {
    @Autowired CatalogService catalog;
    @Autowired AllocationRepository allocations;
    @Autowired RoomRepository rooms;
    @Test
    void seedsCoherentRankingMaintenanceAndReservations() {
        assertThat(rooms.count()).isEqualTo(7);
        assertThat(allocations.count()).isEqualTo(4);
        assertThat(catalog.room(1).timesBooked()).isEqualTo(2);
        assertThat(catalog.room(2).timesBooked()).isEqualTo(1);
        assertThat(catalog.room(5).available()).isFalse();
        var start = LocalDate.now(ZoneOffset.UTC).plusDays(10);
        assertThat(catalog.freeRooms(null, start, start.plusDays(2), true, 0, 20).content())
                .extracting(ru.mephi.common.RoomView::id)
                .containsExactly(3L, 4L, 6L, 7L);
    }
}
