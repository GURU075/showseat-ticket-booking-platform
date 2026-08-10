package com.guru.seat_inventory_service.dto;

import com.guru.seat_inventory_service.entity.SeatStatus;

public record SeatResponse(
        Long showId,
        String seatNumber,
        SeatStatus status
) {
}
