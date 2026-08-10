package com.guru.seat_inventory_service.client.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "show-service", url = "${clients.show-service.base-url}")
public interface ShowServiceFeignClient {

    @GetMapping("/api/v1/shows/{showId}")
    ShowServiceResponse getShow(@PathVariable("showId") Long showId);

    record ShowServiceResponse(Long id, Long venueId, Long screenId, String status) {
    }
}
