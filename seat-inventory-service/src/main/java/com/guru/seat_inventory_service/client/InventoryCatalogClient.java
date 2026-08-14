package com.guru.seat_inventory_service.client;

import java.util.List;

public interface InventoryCatalogClient {

    ShowSummary getShow(Long showId);

    ScreenSummary getScreen(Long screenId);

    List<ScreenSeatSummary> getScreenSeats(Long screenId);

    record ShowSummary(Long id, Long venueId, Long screenId, String status) {
    }

    record ScreenSummary(Long id, Long venueId) {
    }

    record ScreenSeatSummary(String seatNumber, Long screenId) {
    }
}
