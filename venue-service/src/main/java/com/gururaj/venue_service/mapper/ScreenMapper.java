package com.gururaj.venue_service.mapper;

import com.gururaj.venue_service.dto.ScreenCreateRequest;
import com.gururaj.venue_service.dto.ScreenResponse;
import com.gururaj.venue_service.entity.Screen;
import org.springframework.stereotype.Component;

@Component
public class ScreenMapper {

    public Screen toEntity(ScreenCreateRequest request) {
        Screen screen = new Screen();
        screen.setName(request.name().trim());
        screen.setTotalSeats(request.totalSeats());
        return screen;
    }

    public ScreenResponse toResponse(Screen screen) {
        return new ScreenResponse(
                screen.getId(),
                screen.getName(),
                screen.getTotalSeats(),
                screen.getVenue().getId(),
                screen.getVenue().getName()
        );
    }
}
