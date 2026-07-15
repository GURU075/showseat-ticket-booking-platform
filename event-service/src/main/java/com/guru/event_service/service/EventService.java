package com.guru.event_service.service;

import com.guru.event_service.dto.EventRequestDTO;
import com.guru.event_service.dto.EventRequestUpdateDTO;
import com.guru.event_service.dto.EventResponseDTO;

import java.util.List;

public interface EventService {

     EventResponseDTO createEvent(EventRequestDTO eventRequestDTO);

     List<EventResponseDTO> getAllEvents();

     EventResponseDTO getEventById(String id);

     List<EventResponseDTO> searchEvents(String keyword);

     EventResponseDTO updateEvent(EventRequestUpdateDTO eventRequestDTO);

     String deleteEvent(String id);
}
