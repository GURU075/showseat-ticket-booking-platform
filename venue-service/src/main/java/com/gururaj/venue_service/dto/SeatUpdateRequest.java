package com.gururaj.venue_service.dto;

import com.gururaj.venue_service.entity.SeatType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record SeatUpdateRequest(
        @NotNull(message = "Seat id is required")
        Long id,

        @Size(max = 20, message = "Seat number must be 20 characters or fewer")
        String seatNumber,

        @Size(max = 10, message = "Row name must be 10 characters or fewer")
        String rowName,

        @PositiveOrZero(message = "Seat index cannot be negative")
        Integer seatIndex,

        SeatType seatType,

        Long screenId
) {
}
