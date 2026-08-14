package com.guru.seat_inventory_service.dto;

import com.guru.seat_inventory_service.entity.SeatStatus;

import java.util.List;

public record SeatStatusChangeResponse(
        Long showId,
        List<String> seatNumbers,
        SeatStatus status
) {
}
