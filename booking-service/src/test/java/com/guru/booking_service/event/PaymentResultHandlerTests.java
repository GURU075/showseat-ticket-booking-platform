package com.guru.booking_service.event;

import com.guru.booking_service.client.SeatInventoryClient;
import com.guru.booking_service.domain.Booking;
import com.guru.booking_service.service.BookingPersistenceService;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentResultHandlerTests {
    private BookingPersistenceService persistence;
    private SeatInventoryClient seats;
    private PaymentResultHandler handler;

    @BeforeEach void setUp() {
        persistence = mock(BookingPersistenceService.class);
        seats = mock(SeatInventoryClient.class);
        handler = new PaymentResultHandler(persistence, seats);
    }

    @Test void confirmsSeatsBeforeCommittingSuccessfulPaymentEvent() {
        Booking booking = pendingBooking();
        PaymentResultEvent event = event("PAYMENT_SUCCEEDED", booking);
        when(persistence.getPendingOrLater(booking.getId())).thenReturn(booking);

        handler.handle(event);

        var order = inOrder(seats, persistence);
        order.verify(seats).confirm(any());
        order.verify(persistence).confirmFromPayment(booking.getId(), event.paymentId(), event.eventId(), event.eventType());
    }

    @Test void releasesSeatsWhenPaymentFails() {
        Booking booking = pendingBooking();
        PaymentResultEvent event = event("PAYMENT_FAILED", booking);
        when(persistence.getPendingOrLater(booking.getId())).thenReturn(booking);

        handler.handle(event);

        verify(seats).release(any());
        verify(persistence).cancelFromPayment(booking.getId(), event.paymentId(), event.eventId(), event.eventType());
    }

    private Booking pendingBooking() {
        Instant now = Instant.parse("2026-08-25T10:00:00Z");
        Booking booking = Booking.creating(10L, 20L, List.of("A1"), "booking-key", "a".repeat(64), now);
        booking.markPending(new BigDecimal("500.00"), "INR", "LOCK-123", now.plusSeconds(300), now);
        return booking;
    }
    private PaymentResultEvent event(String type, Booking booking) {
        return new PaymentResultEvent(1, UUID.randomUUID(), type, UUID.randomUUID(), booking.getId(),
                booking.getUserId(), booking.getTotalAmount(), booking.getCurrency(),
                type.equals("PAYMENT_FAILED") ? "declined" : null, Instant.now());
    }
}
