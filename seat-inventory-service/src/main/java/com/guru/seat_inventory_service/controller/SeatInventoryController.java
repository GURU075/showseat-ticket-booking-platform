package com.guru.seat_inventory_service.controller;

import com.guru.seat_inventory_service.dto.*;
import com.guru.seat_inventory_service.entity.SeatStatus;
import com.guru.seat_inventory_service.service.SeatInventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/seats")
@RequiredArgsConstructor
@Validated
public class SeatInventoryController {

    private final SeatInventoryService seatInventoryService;

    @PostMapping("/shows/{showId}")
    @ResponseStatus(HttpStatus.CREATED)
    public List<SeatResponse> createSeats(@PathVariable @Positive Long showId) {
        return seatInventoryService.createSeats(showId);
    }

    @GetMapping("/shows/{showId}")
    public List<SeatResponse> getSeats(
            @PathVariable @Positive Long showId,
            @RequestParam(required = false) SeatStatus status
    ) {
        return seatInventoryService.getSeats(showId, status);
    }

    @PostMapping("/lock")
    public SeatLockResponse lockSeats(@Valid @RequestBody LockSeatsRequest request) {
        return seatInventoryService.lockSeats(request);
    }

    @PostMapping("/confirm")
    public SeatActionResponse confirmSeats(@Valid @RequestBody LockActionRequest request) {
        return seatInventoryService.confirmSeats(request);
    }

    @PostMapping("/release")
    public SeatActionResponse releaseSeats(@Valid @RequestBody LockActionRequest request) {
        return seatInventoryService.releaseSeats(request);
    }

    @PostMapping("/block")
    public SeatStatusChangeResponse blockSeats(@Valid @RequestBody BlockUnblockRequest request) {
        return seatInventoryService.blockSeats(request);
    }

    @PostMapping("/unblock")
    public SeatStatusChangeResponse unblockSeats(@Valid @RequestBody BlockUnblockRequest request) {
        return seatInventoryService.unblockSeats(request);
    }
}
