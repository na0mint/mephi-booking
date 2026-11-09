package ru.mephi.booking;

import java.time.LocalDate;
import java.util.UUID;
import ru.mephi.common.*;
public interface HotelPort {
    RoomView confirm(Long roomId, Long hotelId, SlotRequest request);
    void release(UUID bookingId);
    PageDto<RoomView> recommend(
            Long hotelId, LocalDate start, LocalDate end, int page, int size, String bearerToken);
}
