package ru.mephi.booking;

public class HotelRejectedException extends RuntimeException {
    public HotelRejectedException() {
        super("Requested room or hotel is unavailable");
    }
}
