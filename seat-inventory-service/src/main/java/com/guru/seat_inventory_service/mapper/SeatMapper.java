package com.guru.seat_inventory_service.mapper;

import com.guru.seat_inventory_service.dto.SeatResponse;
import com.guru.seat_inventory_service.entity.ShowSeat;
import org.springframework.stereotype.Component;

@Component
public class SeatMapper {

    public SeatResponse toResponse(ShowSeat seat) {
        return new SeatResponse(seat.getShowId(), seat.getSeatNumber(), seat.getStatus());
    }
}
