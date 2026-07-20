package com.gururaj.venue_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CityCreateRequest(
        @NotBlank(message = "City name is required")
        @Size(max = 100, message = "City name must be 100 characters or fewer")
        String name,

        @NotBlank(message = "State is required")
        @Size(max = 100, message = "State must be 100 characters or fewer")
        String state,

        @NotBlank(message = "Country is required")
        @Size(max = 100, message = "Country must be 100 characters or fewer")
        String country
) {
}
