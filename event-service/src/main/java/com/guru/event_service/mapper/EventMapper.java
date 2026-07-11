package com.guru.event_service.mapper;

import com.guru.event_service.dto.EventRequestDTO;
import com.guru.event_service.dto.EventResponseDTO;
import com.guru.event_service.entity.Event;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EventMapper {


    Event toEntity(EventRequestDTO requestDTO);

    EventResponseDTO toResponseDTO(Event event);
}
