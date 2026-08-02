package com.gururaj.show_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UpdateShowRequest(
        @NotNull(message = "Event ID is required")
        @Positive(message = "Event ID must be positive")
        String eventId,

        @NotNull(message = "Venue ID is required")
        @Positive(message = "Venue ID must be positive")
        Long venueId,

        @NotNull(message = "Screen ID is required")
        @Positive(message = "Screen ID must be positive")
        Long screenId,

        @NotNull(message = "Start time is required")
        @Future(message = "Start time must be in the future")
        LocalDateTime startTime,

        @NotNull(message = "End time is required")
        @Future(message = "End time must be in the future")
        LocalDateTime endTime,

        @NotNull(message = "Base price is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "Base price must be greater than zero")
        @Digits(integer = 8, fraction = 2, message = "Base price must have at most 8 integer and 2 decimal digits")
        BigDecimal basePrice
) {
}
