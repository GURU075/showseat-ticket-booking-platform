package com.gururaj.venue_service.dto;

public record CityResponse(
        Long id,
        String name,
        String state,
        String country
) {
}
