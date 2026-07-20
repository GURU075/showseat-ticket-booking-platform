package com.gururaj.venue_service.dto;

public record VenueResponse(
        Long id,
        String name,
        String address,
        String pincode,
        CityResponse city
) {
}
