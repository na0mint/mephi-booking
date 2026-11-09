package ru.mephi.hotel;

import org.springframework.data.jpa.repository.JpaRepository;
public interface HotelRepository extends JpaRepository<Hotel, Long> {
    org.springframework.data.domain.Page<Hotel> findByNameContainingIgnoreCase(
            String name, org.springframework.data.domain.Pageable pageable);
}
