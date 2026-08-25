package com.guru.booking_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.Instant;
import java.util.List;

@FeignClient(name = "seat-inventory-service")
public interface SeatInventoryClient {

    @PostMapping("/api/v1/show-seats/lock")
    SeatLockResponse lock(@RequestBody LockSeatsRequest request);

    @PostMapping("/api/v1/show-seats/release")
    void release(@RequestBody LockActionRequest request);

    record LockSeatsRequest(Long showId, Long userId, List<String> seatNumbers) {
    }

    record LockActionRequest(Long showId, Long userId, String lockId) {
    }

    record SeatLockResponse(
            String lockId,
            Long showId,
            List<String> seatNumbers,
            String status,
            Instant expiresAt
    ) {
    }
}
