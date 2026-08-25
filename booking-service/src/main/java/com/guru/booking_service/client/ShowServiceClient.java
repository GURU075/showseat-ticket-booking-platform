package com.guru.booking_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@FeignClient(name = "show-service")
public interface ShowServiceClient {

    @GetMapping("/api/v1/shows/{showId}")
    ShowDetails getShow(@PathVariable Long showId);

    record ShowDetails(
            Long id,
            String eventId,
            Long venueId,
            Long screenId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            BigDecimal basePrice,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}
