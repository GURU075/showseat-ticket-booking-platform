package com.guru.event_service.controller;

import com.guru.event_service.dto.EventRequestDTO;
import com.guru.event_service.dto.EventResponseDTO;
import com.guru.event_service.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/event")
@RequiredArgsConstructor
public class EventController {

   private final EventService eventService;


    @PostMapping("/create")
    public EventResponseDTO createEvent(@Valid @RequestBody EventRequestDTO eventRequestDTO){
        return eventService.createEvent(eventRequestDTO);
    }

    @GetMapping("/getAll")
    public List<EventResponseDTO> getEvent(){
        return eventService.getAllEvents();
    }
}
