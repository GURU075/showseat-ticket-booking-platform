package com.gururaj.show_service.mapper;

import com.gururaj.show_service.dto.CreateShowRequest;
import com.gururaj.show_service.dto.ShowResponse;
import com.gururaj.show_service.entity.Show;
import org.springframework.stereotype.Component;

@Component
public class ShowMapper {

    public Show toEntity(CreateShowRequest request) {
        return Show.builder()
                .eventId(request.eventId())
                .venueId(request.venueId())
                .screenId(request.screenId())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .basePrice(request.basePrice())
                .build();
    }

    public ShowResponse toResponse(Show show) {
        return new ShowResponse(
                show.getId(),
                show.getEventId(),
                show.getVenueId(),
                show.getScreenId(),
                show.getStartTime(),
                show.getEndTime(),
                show.getBasePrice(),
                show.getStatus(),
                show.getCreatedAt(),
                show.getUpdatedAt()
        );
    }
}
