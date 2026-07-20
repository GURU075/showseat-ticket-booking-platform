package com.gururaj.venue_service.dto;

import com.gururaj.venue_service.entity.SeatType;

public record SeatResponse(
        Long id,
        String seatNumber,
        String rowName,
        Integer seatIndex,
        SeatType seatType,
        Long screenId
) {
}
