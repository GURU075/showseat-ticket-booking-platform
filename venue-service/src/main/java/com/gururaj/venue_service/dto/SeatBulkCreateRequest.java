package com.gururaj.venue_service.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SeatBulkCreateRequest(
        @NotNull(message = "Screen id is required")
        Long screenId,

        @NotEmpty(message = "At least one seat is required")
        @Size(max = 500, message = "Cannot create more than 500 seats in one request")
        List<SeatLayoutRequest> seats
) {
}
