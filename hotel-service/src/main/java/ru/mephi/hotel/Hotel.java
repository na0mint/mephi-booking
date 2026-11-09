package ru.mephi.hotel;

import jakarta.persistence.*;
@Entity
@Table(name = "hotels", indexes = @Index(name = "idx_hotel_name", columnList = "name"))
public class Hotel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false, length = 160) String name;
    @Column(nullable = false, length = 300) String address;
    protected Hotel() {
    }
    Hotel(String name, String address) {
        this.name = name;
        this.address = address;
    }
    public Long getId() {
        return id;
    }
}
