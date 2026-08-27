package com.guru.booking_service.event;

import com.guru.booking_service.client.SeatInventoryClient;
import com.guru.booking_service.domain.*;
import com.guru.booking_service.exception.BookingException;
import com.guru.booking_service.service.BookingPersistenceService;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PaymentResultHandler {
    private final BookingPersistenceService persistence;
    private final SeatInventoryClient seats;
    public PaymentResultHandler(BookingPersistenceService persistence, SeatInventoryClient seats) {
        this.persistence = persistence; this.seats = seats;
    }

    public void handle(PaymentResultEvent event) {
        if (event.version() != 1) throw new IllegalArgumentException("Unsupported payment event version " + event.version());
        if (persistence.paymentEventProcessed(event.eventId())) return;
        Booking booking = persistence.getPendingOrLater(event.bookingId());
        validateSnapshot(booking, event);
        if ("PAYMENT_SUCCEEDED".equals(event.eventType())) {
            if (booking.getStatus() == BookingStatus.CONFIRMED && event.paymentId().equals(booking.getPaymentId())) return;
            if (booking.getStatus() != BookingStatus.PENDING) throw conflict("A successful payment arrived for a non-pending booking");
            seats.confirm(action(booking));
            persistence.confirmFromPayment(booking.getId(), event.paymentId(), event.eventId(), event.eventType());
            return;
        }
        if ("PAYMENT_FAILED".equals(event.eventType())) {
            if (booking.getStatus() == BookingStatus.CANCELLED && event.paymentId().equals(booking.getPaymentId())) return;
            if (booking.getStatus() != BookingStatus.PENDING) throw conflict("A failed payment arrived for a non-pending booking");
            releaseIdempotently(booking);
            persistence.cancelFromPayment(booking.getId(), event.paymentId(), event.eventId(), event.eventType());
            return;
        }
        throw new IllegalArgumentException("Unsupported payment event type " + event.eventType());
    }

    private void validateSnapshot(Booking booking, PaymentResultEvent event) {
        if (!booking.getUserId().equals(event.userId()) || booking.getTotalAmount().compareTo(event.amount()) != 0
                || !booking.getCurrency().equals(event.currency())) {
            throw conflict("Payment event does not match the booking snapshot");
        }
    }
    private SeatInventoryClient.LockActionRequest action(Booking booking) {
        return new SeatInventoryClient.LockActionRequest(booking.getShowId(), booking.getUserId(), booking.getLockId());
    }
    private void releaseIdempotently(Booking booking) {
        try { seats.release(action(booking)); }
        catch (FeignException exception) { if (exception.status() != 404) throw exception; }
    }
    private BookingException conflict(String message) {
        return new BookingException(HttpStatus.CONFLICT, "PAYMENT_EVENT_CONFLICT", message);
    }
}
