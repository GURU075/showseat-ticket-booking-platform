package com.gururaj.venue_service.dto;

import java.util.List;

public record SeatBulkCreateResponse(
        Long screenId,
        int totalRecords,
        int successCount,
        int failureCount,
        List<SeatBulkRecordResponse> records
) {
}
