package com.guru.event_service.controller;

import com.guru.event_service.dto.EventRequestDTO;
import com.guru.event_service.dto.EventRequestUpdateDTO;
import com.guru.event_service.dto.EventResponseDTO;
import com.guru.event_service.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.Query;
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

    @GetMapping("/get/{id}")
    public EventResponseDTO getEventById(@PathVariable String id) {
        return eventService.getEventById(id);
    }

    @GetMapping("/search")
    public List<EventResponseDTO> searchEvents(@RequestParam String keyword) {
        return eventService.searchEvents(keyword);
    }

    @PutMapping("/update")
    public  EventResponseDTO updateEvent(@Valid @RequestBody EventRequestUpdateDTO eventRequestDTO){
        return eventService.updateEvent(eventRequestDTO);
    }

    @DeleteMapping("/delete/{id}")
    public String deleteEvent(@PathVariable String id){
       return eventService.deleteEvent(id);
    }
}
