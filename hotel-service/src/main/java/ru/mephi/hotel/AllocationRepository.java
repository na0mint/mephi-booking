package ru.mephi.hotel;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
public interface AllocationRepository extends JpaRepository<Allocation, UUID> {
    @Query("select count(a)>0 from Allocation a where a.room.id=:roomId and "
            + "a.state=ru.mephi.hotel.Allocation$State.HELD and a.startDate<:endDate and "
            + "a.endDate>:startDate")
    boolean
    overlaps(Long roomId, LocalDate startDate, LocalDate endDate);
    @Query("select a from Allocation a join fetch a.room r where "
            + "a.state=ru.mephi.hotel.Allocation$State.HELD and a.startDate<:endDate and "
            + "a.endDate>:startDate "
            + "and (:hotelId is null or r.hotel.id=:hotelId)")
    List<Allocation>
    inPeriod(Long hotelId, LocalDate startDate, LocalDate endDate);
    boolean existsByRoomId(Long roomId);
}
