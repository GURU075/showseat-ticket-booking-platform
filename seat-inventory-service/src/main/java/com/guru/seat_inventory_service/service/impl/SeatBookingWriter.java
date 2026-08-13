package com.guru.seat_inventory_service.service.impl;

import com.guru.seat_inventory_service.entity.SeatStatus;
import com.guru.seat_inventory_service.entity.ShowSeat;
import com.guru.seat_inventory_service.exception.ResourceNotFoundException;
import com.guru.seat_inventory_service.exception.SeatConflictException;
import com.guru.seat_inventory_service.repository.ShowSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SeatBookingWriter {

    private final ShowSeatRepository showSeatRepository;

    @Transactional
    public List<ShowSeat> confirm(Long showId, List<String> seatNumbers) {
        List<ShowSeat> seats = showSeatRepository.findRequestedSeatsForUpdate(showId, seatNumbers);
        List<String> foundNumbers = seats.stream().map(ShowSeat::getSeatNumber).toList();
        List<String> missingNumbers = seatNumbers.stream()
                .filter(seatNumber -> !foundNumbers.contains(seatNumber))
                .toList();
        if (!missingNumbers.isEmpty()) {
            throw new ResourceNotFoundException(
                    "Seats not found for show " + showId + ": " + String.join(", ", missingNumbers)
            );
        }

        List<String> conflictingSeats = seats.stream()
                .filter(seat -> seat.getStatus() != SeatStatus.AVAILABLE)
                .map(seat -> seat.getSeatNumber() + " (" + seat.getStatus() + ")")
                .toList();
        if (!conflictingSeats.isEmpty()) {
            throw new SeatConflictException(
                    "Seats cannot be booked: " + String.join(", ", conflictingSeats)
            );
        }

        seats.forEach(ShowSeat::book);
        return seats;
    }
}
