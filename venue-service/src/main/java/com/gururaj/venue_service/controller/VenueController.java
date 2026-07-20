package com.gururaj.venue_service.controller;

import com.gururaj.venue_service.dto.VenueCreateRequest;
import com.gururaj.venue_service.dto.VenueResponse;
import com.gururaj.venue_service.dto.VenueUpdateRequest;
import com.gururaj.venue_service.service.VenueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/venues")
@RequiredArgsConstructor
public class VenueController {

    private final VenueService venueService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VenueResponse createVenue(@Valid @RequestBody VenueCreateRequest request) {
        return venueService.createVenue(request);
    }

    @GetMapping
    public List<VenueResponse> getAllVenues(@RequestParam(required = false) Long cityId) {
        return venueService.getAllVenues(cityId);
    }

    @GetMapping("/{id}")
    public VenueResponse getVenueById(@PathVariable Long id) {
        return venueService.getVenueById(id);
    }

    @PutMapping
    public VenueResponse updateVenue(@Valid @RequestBody VenueUpdateRequest request) {
        return venueService.updateVenue(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVenue(@PathVariable Long id) {
        venueService.deleteVenue(id);
    }
}
