package com.guru.booking_service.service;

import com.guru.booking_service.domain.Booking;
import com.guru.booking_service.exception.BookingException;
import com.guru.booking_service.repository.BookingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class BookingPersistenceService {

    private final BookingRepository repository;
    private final Clock clock;

    public BookingPersistenceService(BookingRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Booking reserve(
            Long userId,
            Long showId,
            List<String> seatNumbers,
            String idempotencyKey,
            String requestHash
    ) {
        Booking booking = Booking.creating(
                userId,
                showId,
                seatNumbers,
                idempotencyKey,
                requestHash,
                Instant.now(clock)
        );
        return repository.saveAndFlush(booking);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<Booking> findByIdempotencyKey(Long userId, String idempotencyKey) {
        return repository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Booking complete(
            UUID bookingId,
            BigDecimal totalAmount,
            String currency,
            String lockId,
            Instant lockExpiresAt
    ) {
        Booking booking = repository.findById(bookingId).orElseThrow(() -> new BookingException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "BOOKING_RESERVATION_LOST",
                "The in-progress booking record no longer exists"
        ));
        booking.markPending(totalAmount, currency, lockId, lockExpiresAt, Instant.now(clock));
        return repository.saveAndFlush(booking);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void removeIncomplete(UUID bookingId) {
        repository.findById(bookingId)
                .filter(booking -> booking.getStatus() == com.guru.booking_service.domain.BookingStatus.CREATING)
                .ifPresent(repository::delete);
    }

    @Transactional(readOnly = true)
    public Booking getPendingOrLater(UUID bookingId) {
        return repository.findById(bookingId)
                .filter(booking -> booking.getStatus()
                        != com.guru.booking_service.domain.BookingStatus.CREATING)
                .orElseThrow(() -> new BookingException(
                        HttpStatus.NOT_FOUND,
                        "BOOKING_NOT_FOUND",
                        "Booking " + bookingId + " was not found"
                ));
    }
}
