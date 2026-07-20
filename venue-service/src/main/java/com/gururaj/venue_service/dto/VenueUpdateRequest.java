package com.gururaj.venue_service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VenueUpdateRequest(
        @NotNull(message = "Venue id is required")
        Long id,

        @Size(max = 150, message = "Venue name must be 150 characters or fewer")
        String name,

        @Size(max = 250, message = "Address must be 250 characters or fewer")
        String address,

        @Size(max = 20, message = "Pincode must be 20 characters or fewer")
        String pincode,

        Long cityId
) {
}
