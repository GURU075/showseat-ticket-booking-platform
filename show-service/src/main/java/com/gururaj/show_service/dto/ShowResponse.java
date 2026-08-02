package com.gururaj.show_service.dto;

import com.gururaj.show_service.entity.ShowStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShowResponse(
        Long id,
        String eventId,
        Long venueId,
        Long screenId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        BigDecimal basePrice,
        ShowStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
