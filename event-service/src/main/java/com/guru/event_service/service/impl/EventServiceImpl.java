package com.guru.event_service.service.impl;

import com.guru.event_service.dto.EventRequestDTO;
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
        event.setCreatedAt(LocalDateTime.now());
        event.setUpdatedAt(LocalDateTime.now());
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
}
