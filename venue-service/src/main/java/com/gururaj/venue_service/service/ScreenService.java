package com.gururaj.venue_service.service;

import com.gururaj.venue_service.dto.ScreenCreateRequest;
import com.gururaj.venue_service.dto.ScreenResponse;
import com.gururaj.venue_service.dto.ScreenUpdateRequest;

import java.util.List;

public interface ScreenService {

    ScreenResponse createScreen(ScreenCreateRequest request);

    List<ScreenResponse> getAllScreens(Long venueId);

    ScreenResponse getScreenById(Long id);

    ScreenResponse updateScreen(ScreenUpdateRequest request);

    void deleteScreen(Long id);
}
