package com.guru.booking_service.service;

import com.guru.booking_service.api.BookingResponse;
import com.guru.booking_service.api.CreateBookingRequest;
import com.guru.booking_service.client.SeatInventoryClient;
import com.guru.booking_service.client.ShowServiceClient;
import com.guru.booking_service.domain.Booking;
import com.guru.booking_service.domain.BookingStatus;
import com.guru.booking_service.exception.BookingException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingPersistenceService persistence;
    private final ShowServiceClient showClient;
    private final SeatInventoryClient seatClient;
    private final Clock clock;
    private final String currency;

    public BookingService(
            BookingPersistenceService persistence,
            ShowServiceClient showClient,
            SeatInventoryClient seatClient,
            Clock clock,
            @Value("${booking.currency:INR}") String currency
    ) {
        this.persistence = persistence;
        this.showClient = showClient;
        this.seatClient = seatClient;
        this.clock = clock;
        this.currency = currency;
    }

    public CreationResult create(CreateBookingRequest request, String idempotencyKey) {
        List<String> seats = normalizeSeats(request.seatNumbers());
        String requestHash = requestHash(request.userId(), request.showId(), seats);

        Booking reservation;
        try {
            reservation = persistence.reserve(
                    request.userId(), request.showId(), seats, idempotencyKey, requestHash
            );
        }
        catch (DataIntegrityViolationException duplicateKey) {
            return replayOrReject(request.userId(), idempotencyKey, requestHash);
        }

        SeatInventoryClient.SeatLockResponse lock = null;
        try {
            ShowServiceClient.ShowDetails show = loadBookableShow(request.showId());
            BigDecimal total = show.basePrice()
                    .multiply(BigDecimal.valueOf(seats.size()))
                    .setScale(2, RoundingMode.HALF_UP);
            lock = acquireLock(request.userId(), request.showId(), seats);
            validateLock(lock, request.showId(), seats);

            Booking completed = persistence.complete(
                    reservation.getId(), total, currency, lock.lockId(), lock.expiresAt()
            );
            return new CreationResult(BookingResponse.from(completed), false);
        }
        catch (RuntimeException failure) {
            if (lock != null && lock.lockId() != null) {
                releaseBestEffort(request.userId(), request.showId(), lock.lockId());
            }
            persistence.removeIncomplete(reservation.getId());
            throw failure;
        }
    }

    public BookingResponse get(UUID bookingId) {
        return BookingResponse.from(persistence.getPendingOrLater(bookingId));
    }

    private CreationResult replayOrReject(Long userId, String key, String requestHash) {
        Booking existing = persistence.findByIdempotencyKey(userId, key)
                .orElseThrow(() -> new BookingException(
                        HttpStatus.CONFLICT,
                        "IDEMPOTENCY_RACE",
                        "Another request is currently using this idempotency key"
                ));
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new BookingException(
                    HttpStatus.CONFLICT,
                    "IDEMPOTENCY_KEY_REUSED",
                    "The Idempotency-Key was already used with a different booking request"
            );
        }
        if (existing.getStatus() == BookingStatus.CREATING) {
            throw new BookingException(
                    HttpStatus.CONFLICT,
                    "BOOKING_CREATION_IN_PROGRESS",
                    "A booking with this Idempotency-Key is still being created"
            );
        }
        return new CreationResult(BookingResponse.from(existing), true);
    }

    private ShowServiceClient.ShowDetails loadBookableShow(Long showId) {
        ShowServiceClient.ShowDetails show;
        try {
            show = showClient.getShow(showId);
        }
        catch (FeignException exception) {
            if (exception.status() == 404) {
                throw new BookingException(HttpStatus.NOT_FOUND, "SHOW_NOT_FOUND", "Show " + showId + " was not found");
            }
            throw unavailable("Show Service", exception);
        }
        if (!"SCHEDULED".equals(show.status())) {
            throw new BookingException(HttpStatus.CONFLICT, "SHOW_NOT_BOOKABLE", "Only a scheduled show can be booked");
        }
        if (show.startTime() == null || !show.startTime().isAfter(LocalDateTime.now(clock))) {
            throw new BookingException(HttpStatus.CONFLICT, "SHOW_ALREADY_STARTED", "The show has already started");
        }
        if (show.basePrice() == null || show.basePrice().signum() <= 0) {
            throw new BookingException(HttpStatus.SERVICE_UNAVAILABLE, "INVALID_SHOW_PRICE", "Show Service returned an invalid price");
        }
        return show;
    }

    private SeatInventoryClient.SeatLockResponse acquireLock(Long userId, Long showId, List<String> seats) {
        try {
            return seatClient.lock(new SeatInventoryClient.LockSeatsRequest(showId, userId, seats));
        }
        catch (FeignException exception) {
            if (exception.status() == 404) {
                throw new BookingException(HttpStatus.NOT_FOUND, "SHOW_SEATS_NOT_FOUND", "Seat inventory for the show was not found");
            }
            if (exception.status() == 409) {
                throw new BookingException(HttpStatus.CONFLICT, "SEATS_UNAVAILABLE", "One or more selected seats are no longer available");
            }
            throw unavailable("Seat Inventory Service", exception);
        }
    }

    private void validateLock(SeatInventoryClient.SeatLockResponse lock, Long showId, List<String> seats) {
        if (lock == null || lock.lockId() == null || lock.lockId().isBlank()
                || lock.expiresAt() == null || !lock.expiresAt().isAfter(Instant.now(clock))
                || !showId.equals(lock.showId()) || !new HashSet<>(seats).equals(new HashSet<>(lock.seatNumbers()))) {
            throw new BookingException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "INVALID_SEAT_LOCK_RESPONSE",
                    "Seat Inventory Service returned an invalid seat lock"
            );
        }
    }

    private void releaseBestEffort(Long userId, Long showId, String lockId) {
        try {
            seatClient.release(new SeatInventoryClient.LockActionRequest(showId, userId, lockId));
        }
        catch (RuntimeException releaseFailure) {
            // The Redis lock still has a TTL, so failure here cannot hold seats forever.
            log.error("Failed to compensate seat lock bookingShowId={} lockId={}", showId, lockId, releaseFailure);
        }
    }

    private static BookingException unavailable(String service, FeignException cause) {
        return new BookingException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "DOWNSTREAM_SERVICE_UNAVAILABLE",
                service + " is temporarily unavailable"
        );
    }

    private static List<String> normalizeSeats(List<String> suppliedSeats) {
        List<String> normalized = suppliedSeats.stream()
                .map(seat -> seat.trim().toUpperCase(Locale.ROOT))
                .toList();
        Set<String> unique = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (String seat : normalized) {
            if (!unique.add(seat)) {
                duplicates.add(seat);
            }
        }
        if (!duplicates.isEmpty()) {
            throw new BookingException(
                    HttpStatus.BAD_REQUEST,
                    "DUPLICATE_SEATS",
                    "The request contains duplicate seat numbers: " + duplicates
            );
        }
        return normalized;
    }

    private static String requestHash(Long userId, Long showId, List<String> seats) {
        List<String> canonicalSeats = seats.stream().sorted().toList();
        String canonical = userId + "|" + showId + "|" + String.join(",", canonicalSeats);
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(canonical.getBytes(StandardCharsets.UTF_8))
            );
        }
        catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public record CreationResult(BookingResponse booking, boolean replayed) {
    }
}
