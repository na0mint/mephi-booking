package ru.mephi.hotel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
public interface RoomRepository extends JpaRepository<Room, Long>, JpaSpecificationExecutor<Room> {
    @org.springframework.data.jpa.repository.Query("select count(r)>0 from Room r where r.hotel.id=:hotelId")
    boolean existsByHotelId(Long hotelId);
}
