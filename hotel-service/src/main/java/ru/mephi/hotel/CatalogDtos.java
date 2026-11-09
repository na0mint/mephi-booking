package ru.mephi.hotel;

import jakarta.validation.constraints.*;
public final class CatalogDtos {
    private CatalogDtos() {
    }
    public record HotelInput(
            @NotBlank @Size(max = 160) String name, @NotBlank @Size(max = 300) String address) {
    }
    public record HotelView(Long id, String name, String address) {
        static HotelView from(Hotel h) {
            return new HotelView(h.id, h.name, h.address);
        }
    }
    public record RoomInput(@NotNull @Positive Long hotelId, @NotBlank @Size(max = 30) String number,
            @NotNull Boolean available) {
    }
}
