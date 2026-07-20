package com.gururaj.venue_service.dto;

import com.gururaj.venue_service.entity.SeatType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record SeatCreateRequest(
        @NotBlank(message = "Seat number is required")
        @Size(max = 20, message = "Seat number must be 20 characters or fewer")
        String seatNumber,

        @NotBlank(message = "Row name is required")
        @Size(max = 10, message = "Row name must be 10 characters or fewer")
        String rowName,

        @NotNull(message = "Seat index is required")
        @PositiveOrZero(message = "Seat index cannot be negative")
        Integer seatIndex,

        @NotNull(message = "Seat type is required")
        SeatType seatType,

        @NotNull(message = "Screen id is required")
        Long screenId
) {
}
