package com.gururaj.venue_service.dto;

public record SeatBulkRecordResponse(
        int recordIndex,
        String seatNumber,
        String status,
        String message,
        SeatResponse seat
) {
}
