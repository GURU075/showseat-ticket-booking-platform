package com.gururaj.show_service.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateShowRequest(
        @NotBlank(message ="Event ID is required")
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
