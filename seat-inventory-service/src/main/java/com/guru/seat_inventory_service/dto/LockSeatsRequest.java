package com.guru.seat_inventory_service.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record LockSeatsRequest(
        @NotNull @Positive Long showId,
        @NotNull @Positive Long userId,
        @NotEmpty(message = "seatNumbers must contain at least one seat")
        @Size(max = 20, message = "at most 20 seats can be locked in one request")
        List<String> seatNumbers
) {
}
