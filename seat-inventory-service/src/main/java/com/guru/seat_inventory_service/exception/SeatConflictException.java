package com.guru.seat_inventory_service.exception;

public class SeatConflictException extends RuntimeException {

    public SeatConflictException(String message) {
        super(message);
    }
}
