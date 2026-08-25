package com.guru.booking_service.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateBookingRequest(
        @NotNull @Positive Long userId,
        @NotNull @Positive Long showId,
        @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 20) String> seatNumbers
) {
}
