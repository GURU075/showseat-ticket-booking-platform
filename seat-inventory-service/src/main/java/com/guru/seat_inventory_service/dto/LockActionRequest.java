package com.guru.seat_inventory_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record LockActionRequest(
        @NotNull @Positive Long showId,
        @NotNull @Positive Long userId,
        @NotBlank String lockId
) {
}
