package com.guru.booking_service.service;

import com.guru.booking_service.client.PaymentClient;
import com.guru.booking_service.domain.*;
import com.guru.booking_service.exception.BookingException;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.UUID;

@Service
public class PaymentCheckoutService {
    private final BookingPersistenceService persistence;
    private final PaymentClient payments;
    private final Clock clock;
    public PaymentCheckoutService(BookingPersistenceService persistence, PaymentClient payments, Clock clock) {
        this.persistence = persistence; this.payments = payments; this.clock = clock;
    }

    public PaymentClient.PaymentDetails create(UUID bookingId, String key) {
        Booking booking = persistence.getPendingOrLater(bookingId);
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BookingException(HttpStatus.CONFLICT, "BOOKING_NOT_PENDING", "Only a pending booking can be paid");
        }
        if (!booking.getLockExpiresAt().isAfter(Instant.now(clock))) {
            throw new BookingException(HttpStatus.CONFLICT, "PAYMENT_DEADLINE_PASSED", "The seat lock has expired");
        }
        try {
            return payments.create(key, new PaymentClient.CreatePayment(booking.getId(), booking.getUserId(),
                    booking.getTotalAmount(), booking.getCurrency()));
        } catch (FeignException exception) {
            if (exception.status() == 409) throw new BookingException(HttpStatus.CONFLICT, "PAYMENT_CONFLICT", "Payment Service rejected conflicting payment data");
            throw new BookingException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_SERVICE_UNAVAILABLE", "Payment Service is temporarily unavailable");
        }
    }
}
