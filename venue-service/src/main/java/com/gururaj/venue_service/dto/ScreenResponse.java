package com.gururaj.venue_service.dto;

public record ScreenResponse(
        Long id,
        String name,
        Integer totalSeats,
        Long venueId,
        String venueName
) {
}
