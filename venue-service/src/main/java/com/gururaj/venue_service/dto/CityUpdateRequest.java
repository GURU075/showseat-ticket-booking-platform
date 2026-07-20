package com.gururaj.venue_service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CityUpdateRequest(
        @NotNull(message = "City id is required")
        Long id,

        @Size(max = 100, message = "City name must be 100 characters or fewer")
        String name,

        @Size(max = 100, message = "State must be 100 characters or fewer")
        String state,

        @Size(max = 100, message = "Country must be 100 characters or fewer")
        String country
) {
}
