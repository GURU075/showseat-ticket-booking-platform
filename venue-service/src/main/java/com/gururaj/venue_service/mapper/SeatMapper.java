package com.gururaj.venue_service.mapper;

import com.gururaj.venue_service.dto.SeatCreateRequest;
import com.gururaj.venue_service.dto.SeatResponse;
import com.gururaj.venue_service.entity.Seat;
import org.springframework.stereotype.Component;

@Component
public class SeatMapper {

    public Seat toEntity(SeatCreateRequest request) {
        Seat seat = new Seat();
        seat.setSeatNumber(request.seatNumber().trim());
        seat.setRowName(request.rowName().trim());
        seat.setSeatIndex(request.seatIndex());
        seat.setSeatType(request.seatType());
        return seat;
    }

    public SeatResponse toResponse(Seat seat) {
        return new SeatResponse(
                seat.getId(),
                seat.getSeatNumber(),
                seat.getRowName(),
                seat.getSeatIndex(),
                seat.getSeatType(),
                seat.getScreen().getId()
        );
    }
}
