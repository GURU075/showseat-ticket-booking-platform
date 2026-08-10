package com.guru.seat_inventory_service.client.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "venue-service", url = "${clients.venue-service.base-url}")
public interface VenueServiceFeignClient {

    @GetMapping("/api/v1/screens/{screenId}")
    ScreenServiceResponse getScreen(@PathVariable("screenId") Long screenId);

    @GetMapping("/api/v1/seats")
    List<SeatServiceResponse> getScreenSeats(@RequestParam("screenId") Long screenId);

    record ScreenServiceResponse(Long id, Long venueId) {
    }

    record SeatServiceResponse(String seatNumber, Long screenId) {
    }
}
