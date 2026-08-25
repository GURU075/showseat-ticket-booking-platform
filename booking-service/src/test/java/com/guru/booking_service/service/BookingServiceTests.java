package com.guru.booking_service.service;

import com.guru.booking_service.api.CreateBookingRequest;
import com.guru.booking_service.client.SeatInventoryClient;
import com.guru.booking_service.client.ShowServiceClient;
import com.guru.booking_service.domain.Booking;
import com.guru.booking_service.exception.BookingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingServiceTests {

    private static final Instant NOW = Instant.parse("2026-08-22T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("Asia/Kolkata"));

    private BookingPersistenceService persistence;
    private ShowServiceClient shows;
    private SeatInventoryClient seats;
    private BookingService service;

    @BeforeEach
    void setUp() {
        persistence = mock(BookingPersistenceService.class);
        shows = mock(ShowServiceClient.class);
        seats = mock(SeatInventoryClient.class);
        service = new BookingService(persistence, shows, seats, CLOCK, "INR");
    }

    @Test
    void createsPendingBookingFromShowPriceAndSeatLock() {
        Booking creating = creatingBooking();
        when(persistence.reserve(any(), any(), any(), any(), any())).thenReturn(creating);
        when(shows.getShow(20L)).thenReturn(bookableShow());
        when(seats.lock(any())).thenReturn(lock());
        creating.markPending(new BigDecimal("500.00"), "INR", "lock-1", NOW.plusSeconds(300), NOW);
        when(persistence.complete(any(), any(), any(), any(), any())).thenReturn(creating);

        BookingService.CreationResult result = service.create(request(), "checkout-1");

        assertThat(result.replayed()).isFalse();
        assertThat(result.booking().status().name()).isEqualTo("PENDING");
        assertThat(result.booking().totalAmount()).isEqualByComparingTo("500.00");
        ArgumentCaptor<SeatInventoryClient.LockSeatsRequest> lockRequest =
                ArgumentCaptor.forClass(SeatInventoryClient.LockSeatsRequest.class);
        verify(seats).lock(lockRequest.capture());
        assertThat(lockRequest.getValue().seatNumbers()).containsExactly("A1", "A2");
    }

    @Test
    void replaysCompletedBookingWithoutLockingSeatsAgain() {
        Booking existing = creatingBooking();
        existing.markPending(new BigDecimal("500.00"), "INR", "lock-1", NOW.plusSeconds(300), NOW);
        when(persistence.reserve(any(), any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        when(persistence.findByIdempotencyKey(10L, "checkout-1"))
                .thenReturn(Optional.of(existing));

        BookingService.CreationResult result = service.create(request(), "checkout-1");

        assertThat(result.replayed()).isTrue();
        assertThat(result.booking().id()).isEqualTo(existing.getId());
        verify(shows, never()).getShow(any());
        verify(seats, never()).lock(any());
    }

    @Test
    void releasesLockWhenDatabaseCompletionFails() {
        Booking creating = creatingBooking();
        when(persistence.reserve(any(), any(), any(), any(), any())).thenReturn(creating);
        when(shows.getShow(20L)).thenReturn(bookableShow());
        when(seats.lock(any())).thenReturn(lock());
        when(persistence.complete(any(), any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("database failure"));

        assertThatThrownBy(() -> service.create(request(), "checkout-1"))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(seats).release(new SeatInventoryClient.LockActionRequest(20L, 10L, "lock-1"));
        verify(persistence).removeIncomplete(creating.getId());
    }

    @Test
    void rejectsDuplicateSeatsBeforeCallingAnyDependency() {
        CreateBookingRequest invalid = new CreateBookingRequest(10L, 20L, List.of("a1", " A1 "));

        assertThatThrownBy(() -> service.create(invalid, "checkout-1"))
                .isInstanceOfSatisfying(BookingException.class,
                        exception -> assertThat(exception.code()).isEqualTo("DUPLICATE_SEATS"));
        verify(persistence, never()).reserve(any(), any(), any(), any(), any());
    }

    private static CreateBookingRequest request() {
        return new CreateBookingRequest(10L, 20L, List.of("a1", " a2 "));
    }

    private static Booking creatingBooking() {
        return Booking.creating(
                10L,
                20L,
                List.of("A1", "A2"),
                "checkout-1",
                "9cc02a294ce03e9ce090b3dd613bcdfee8d6df228cca9859be3b06226034a8d4",
                NOW
        );
    }

    private static ShowServiceClient.ShowDetails bookableShow() {
        return new ShowServiceClient.ShowDetails(
                20L, "event-1", 2L, 3L,
                LocalDateTime.now(CLOCK).plusHours(2),
                LocalDateTime.now(CLOCK).plusHours(4),
                new BigDecimal("250.00"), "SCHEDULED", null, null
        );
    }

    private static SeatInventoryClient.SeatLockResponse lock() {
        return new SeatInventoryClient.SeatLockResponse(
                "lock-1", 20L, List.of("A1", "A2"), "LOCKED", NOW.plusSeconds(300)
        );
    }
}
