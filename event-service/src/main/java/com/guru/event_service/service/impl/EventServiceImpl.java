package com.guru.event_service.service.impl;

import com.guru.event_service.dto.EventRequestDTO;
import com.guru.event_service.dto.EventRequestUpdateDTO;
import com.guru.event_service.dto.EventResponseDTO;
import com.guru.event_service.entity.Event;
import com.guru.event_service.mapper.EventMapper;
import com.guru.event_service.repositopry.EventRepository;
import com.guru.event_service.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.swing.text.html.parser.Entity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private final EventMapper eventMapper;
    private final EventRepository eventRepository;

    @Override
    public EventResponseDTO createEvent(EventRequestDTO eventRequestDTO) {

        Event event =eventMapper.toEntity(eventRequestDTO);
        event.setId(UUID.randomUUID().toString());
        event = eventRepository.save(event);
        return eventMapper.toResponseDTO(event);
    }

    @Override
    public List<EventResponseDTO> getAllEvents() {
        return eventRepository.findAll()
                .stream()
                .map(eventMapper::toResponseDTO)
                .toList();
    }

    @Override
    public EventResponseDTO getEventById(String id) {
        return eventRepository.findById(id)
                .map(eventMapper::toResponseDTO)
                .orElse(null);
    }

    @Override
    public List<EventResponseDTO> searchEvents(String keyword) {
        List<Event> events = eventRepository.findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(keyword, keyword);
        return events.stream()
                .map(eventMapper::toResponseDTO)
                .toList();
    }

    @Override
    public EventResponseDTO updateEvent(EventRequestUpdateDTO eventRequestDTO) {
        String eventId = eventRequestDTO.id();
        Event existingEvent = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found with id: " + eventId));


        existingEvent.setTitle(setIfNotNull(existingEvent.getTitle(),eventRequestDTO.title()));
        existingEvent.setDescription(setIfNotNull(existingEvent.getDescription(),eventRequestDTO.description()));
        existingEvent.setDurationMinutes(setIfNotNull(existingEvent.getDurationMinutes(),eventRequestDTO.durationMinutes()));
        existingEvent.setLanguage(setIfNotNull(existingEvent.getLanguage(),eventRequestDTO.language()));
        existingEvent.setStatus(setIfNotNull(existingEvent.getStatus(),eventRequestDTO.status()));

        Event updatedEvent = eventRepository.save(existingEvent);
        return eventMapper.toResponseDTO(updatedEvent);
    }

    @Override
    public String deleteEvent(String id) {

        if (!eventRepository.existsById(id)) {
            throw new RuntimeException("Event not found with id: " + id);
        }
        eventRepository.deleteById(id);
        return "Event deleted successfully with id: " + id;
    }

    String setIfNotNull(String value, String newValue) {
        if (newValue != null && !newValue.isEmpty()) {
            return newValue;
        }
        return value;
    }
    private Integer setIfNotNull(Integer oldValue, Integer newValue) {
        return newValue != null ? newValue : oldValue;
    }
}
