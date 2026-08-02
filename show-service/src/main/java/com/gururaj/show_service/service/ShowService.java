package com.gururaj.show_service.service;

import com.gururaj.show_service.dto.CreateShowRequest;
import com.gururaj.show_service.dto.ShowResponse;
import com.gururaj.show_service.dto.UpdateShowRequest;

import java.util.List;

public interface ShowService {

    ShowResponse createShow(CreateShowRequest request);

    List<ShowResponse> getShows(Long eventId, Long screenId);

    ShowResponse getShowById(Long id);

    ShowResponse updateShow(Long id, UpdateShowRequest request);
}
