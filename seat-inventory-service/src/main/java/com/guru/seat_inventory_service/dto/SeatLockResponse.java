package com.guru.seat_inventory_service.dto;

import com.guru.seat_inventory_service.entity.SeatStatus;

import java.time.LocalDateTime;
import java.util.List;

public record SeatLockResponse(
        String lockId,
        Long showId,
        List<String> seatNumbers,
        SeatStatus status,
        LocalDateTime expiresAt
) {
}
