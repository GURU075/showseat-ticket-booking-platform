package com.guru.seat_inventory_service.service.impl;

import com.guru.seat_inventory_service.entity.SeatStatus;
import com.guru.seat_inventory_service.entity.ShowSeat;
import com.guru.seat_inventory_service.exception.SeatConflictException;
import com.guru.seat_inventory_service.repository.ShowSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SeatInventoryWriter {

    private final ShowSeatRepository showSeatRepository;

    @Transactional
    public List<ShowSeat> create(Long showId, List<String> seatNumbers) {
        // Recheck inside the write transaction to protect against concurrent creation requests.
        if (showSeatRepository.existsByShowId(showId)) {
            throw new SeatConflictException("Seat inventory already exists for show " + showId);
        }

        List<ShowSeat> seats = seatNumbers.stream()
                .map(seatNumber -> ShowSeat.builder()
                        .showId(showId)
                        .seatNumber(seatNumber)
                        .status(SeatStatus.AVAILABLE)
                        .build())
                .toList();
        return showSeatRepository.saveAll(seats);
    }
}
