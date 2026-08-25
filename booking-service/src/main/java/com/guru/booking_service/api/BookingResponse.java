package com.guru.booking_service.api;

import com.guru.booking_service.domain.Booking;
import com.guru.booking_service.domain.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        Long userId,
        Long showId,
        List<String> seatNumbers,
        BookingStatus status,
        BigDecimal totalAmount,
        String currency,
        Instant paymentDeadline,
        Instant createdAt,
        Instant updatedAt
) {
    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getUserId(),
                booking.getShowId(),
                booking.getSeatNumbers(),
                booking.getStatus(),
                booking.getTotalAmount(),
                booking.getCurrency(),
                booking.getLockExpiresAt(),
                booking.getCreatedAt(),
                booking.getUpdatedAt()
        );
    }
}
