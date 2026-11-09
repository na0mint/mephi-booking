package ru.mephi.booking;

public class HotelUnavailableException extends RuntimeException {
    public HotelUnavailableException(Throwable cause) {
        super("Hotel service is temporarily unavailable", cause);
    }
}
