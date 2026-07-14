package com.guru.event_service.service;

import com.guru.event_service.dto.EventRequestDTO;
import com.guru.event_service.dto.EventResponseDTO;

import java.util.List;

public interface EventService {

     EventResponseDTO createEvent(EventRequestDTO eventRequestDTO);

     List<EventResponseDTO> getAllEvents();
}
