package com.gururaj.venue_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ScreenCreateRequest(
        @NotBlank(message = "Screen name is required")
        @Size(max = 100, message = "Screen name must be 100 characters or fewer")
        String name,

        @NotNull(message = "Total seats is required")
        @PositiveOrZero(message = "Total seats cannot be negative")
        Integer totalSeats,

        @NotNull(message = "Venue id is required")
        Long venueId
) {
}
