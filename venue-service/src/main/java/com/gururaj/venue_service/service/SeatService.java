package com.gururaj.venue_service.service;

import com.gururaj.venue_service.dto.SeatCreateRequest;
import com.gururaj.venue_service.dto.SeatBulkCreateRequest;
import com.gururaj.venue_service.dto.SeatBulkCreateResponse;
import com.gururaj.venue_service.dto.SeatResponse;
import com.gururaj.venue_service.dto.SeatUpdateRequest;

import java.util.List;

public interface SeatService {

    SeatResponse createSeat(SeatCreateRequest request);

    SeatBulkCreateResponse createSeats(SeatBulkCreateRequest request);

    List<SeatResponse> getAllSeats(Long screenId);

    SeatResponse getSeatById(Long id);

    SeatResponse updateSeat(SeatUpdateRequest request);

    void deleteSeat(Long id);
}
