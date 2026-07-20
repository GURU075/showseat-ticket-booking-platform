package com.gururaj.venue_service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ScreenUpdateRequest(
        @NotNull(message = "Screen id is required")
        Long id,

        @Size(max = 100, message = "Screen name must be 100 characters or fewer")
        String name,

        @PositiveOrZero(message = "Total seats cannot be negative")
        Integer totalSeats,

        Long venueId
) {
}
