package ru.mephi.hotel;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.common.*;
@Component
public class HotelSeed implements ApplicationRunner {
    private final GuardRepository guard;
    private final HotelRepository hotels;
    private final RoomRepository rooms;
    private final AllocationService allocations;
    private final boolean demo;
    public HotelSeed(GuardRepository guard, HotelRepository hotels, RoomRepository rooms,
            AllocationService allocations, @Value("${demo.seed:true}") boolean demo) {
        this.guard = guard;
        this.hotels = hotels;
        this.rooms = rooms;
        this.allocations = allocations;
        this.demo = demo;
    }
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!guard.existsById(1L))
            guard.saveAndFlush(new AllocationGuard(1));
        if (!demo || hotels.count() > 0)
            return;
        var moscow = hotels.save(new Hotel("MEPhI Campus Hotel", "Moscow, Kashirskoe shosse, 31"));
        var kazan = hotels.save(new Hotel("Volga Hotel", "Kazan, Universitetskaya, 7"));
        for (String number : new String[] {"101", "102", "103", "104"})
            rooms.save(new Room(moscow, number, true));
        rooms.save(new Room(moscow, "105-maintenance", false));
        rooms.save(new Room(kazan, "201", true));
        rooms.save(new Room(kazan, "202", true));
        rooms.flush();
        for (var sample : DemoData.reservations()) {
            if (sample.cancelled())
                allocations.release(sample.roomId(), sample.bookingId());
            else
                allocations.confirm(sample.roomId(), null,
                        new SlotRequest(sample.bookingId(), sample.startDate(), sample.endDate()));
        }
    }
}
