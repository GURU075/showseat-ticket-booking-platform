package com.guru.seat_inventory_service.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BlockUnblockRequest(
        @NotNull @Positive Long showId,

        @NotEmpty(message = "seatNumbers must contain at least one seat")
        @Size(max = 20, message = "at most 20 seats can be changed in one request")
        List<
                @NotBlank(message = "seat number must not be blank")
                @Pattern(
                        regexp = "^\\s*[A-Za-z0-9_-]{1,20}\\s*$",
                        message = "seat number must contain only letters, numbers, underscore, or hyphen"
                ) String
                > seatNumbers
) {
}
