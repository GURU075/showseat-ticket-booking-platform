package com.gururaj.venue_service.service;

import com.gururaj.venue_service.dto.VenueCreateRequest;
import com.gururaj.venue_service.dto.VenueResponse;
import com.gururaj.venue_service.dto.VenueUpdateRequest;

import java.util.List;

public interface VenueService {

    VenueResponse createVenue(VenueCreateRequest request);

    List<VenueResponse> getAllVenues(Long cityId);

    VenueResponse getVenueById(Long id);

    VenueResponse updateVenue(VenueUpdateRequest request);

    void deleteVenue(Long id);
}
