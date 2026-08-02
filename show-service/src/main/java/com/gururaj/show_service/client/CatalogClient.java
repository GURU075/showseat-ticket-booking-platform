package com.gururaj.show_service.client;

public interface CatalogClient {

    void verifyEventExists(String eventId);

    void verifyVenueExists(Long venueId);

    ScreenSummary getScreen(Long screenId);

    record ScreenSummary(Long id, Long venueId) {
    }
}
