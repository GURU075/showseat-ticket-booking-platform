package com.gururaj.venue_service.controller;

import com.gururaj.venue_service.dto.SeatCreateRequest;
import com.gururaj.venue_service.dto.SeatBulkCreateRequest;
import com.gururaj.venue_service.dto.SeatBulkCreateResponse;
import com.gururaj.venue_service.dto.SeatResponse;
import com.gururaj.venue_service.dto.SeatUpdateRequest;
import com.gururaj.venue_service.service.SeatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/seats")
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SeatResponse createSeat(@Valid @RequestBody SeatCreateRequest request) {
        return seatService.createSeat(request);
    }

    @PostMapping("/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    public SeatBulkCreateResponse createSeats(@Valid @RequestBody SeatBulkCreateRequest request) {
        return seatService.createSeats(request);
    }

    @GetMapping
    public List<SeatResponse> getAllSeats(@RequestParam(required = false) Long screenId) {
        return seatService.getAllSeats(screenId);
    }

    @GetMapping("/{id}")
    public SeatResponse getSeatById(@PathVariable Long id) {
        return seatService.getSeatById(id);
    }

    @PutMapping
    public SeatResponse updateSeat(@Valid @RequestBody SeatUpdateRequest request) {
        return seatService.updateSeat(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSeat(@PathVariable Long id) {
        seatService.deleteSeat(id);
    }
}
