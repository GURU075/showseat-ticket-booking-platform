package com.guru.seat_inventory_service.lock;

import java.time.Instant;
import java.util.List;

public record SeatLockDetails(
        String lockId,
        Long showId,
        Long userId,
        List<String> seatNumbers,
        Instant expiresAt
) {
    public SeatLockDetails {
        seatNumbers = List.copyOf(seatNumbers);
    }
}
