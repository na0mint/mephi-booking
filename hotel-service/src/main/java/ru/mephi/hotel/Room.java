package ru.mephi.hotel;

import jakarta.persistence.*;
@Entity
@Table(name = "rooms",
        uniqueConstraints = @UniqueConstraint(name = "uk_room_number", columnNames = {"hotel_id", "number"}),
        indexes =
        {
            @Index(name = "idx_room_rank", columnList = "available,times_booked,id")
            , @Index(name = "idx_room_hotel", columnList = "hotel_id")
        })
public class Room {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false)
    Hotel hotel;
    @Column(nullable = false, length = 30) String number;
    @Column(nullable = false) boolean available;
    @Column(name = "times_booked", nullable = false) long timesBooked;
    @Version long version;
    protected Room() {
    }
    Room(Hotel hotel, String number, boolean available) {
        this.hotel = hotel;
        this.number = number;
        this.available = available;
    }
    ru.mephi.common.RoomView view() {
        return new ru.mephi.common.RoomView(id, hotel.getId(), number, available, timesBooked);
    }
    public Long getId() {
        return id;
    }
    public Long getHotelId() {
        return hotel.getId();
    }
    public String getNumber() {
        return number;
    }
    public boolean isAvailable() {
        return available;
    }
    public long getTimesBooked() {
        return timesBooked;
    }
    public void addBooking() {
        timesBooked++;
    }
    public void removeBooking() {
        timesBooked--;
    }
    public void update(String number, boolean available) {
        this.number = number;
        this.available = available;
    }
}
