package com.guru.payment_service.service;

import com.guru.payment_service.domain.Payment;
import com.guru.payment_service.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentPersistenceService {

    private final PaymentRepository repository;
    private final Clock clock;

    public PaymentPersistenceService(PaymentRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Uses an independent transaction so a unique-constraint failure can be
     * caught by PaymentService and handled as an idempotent replay.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment reserve(
            UUID bookingId,
            Long userId,
            BigDecimal amount,
            String currency,
            String idempotencyKey,
            String requestHash
    ) {
        Payment payment = Payment.pending(
                bookingId,
                userId,
                amount,
                currency,
                idempotencyKey,
                requestHash,
                Instant.now(clock)
        );
        return repository.saveAndFlush(payment);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<Payment> byKey(Long userId, String idempotencyKey) {
        return repository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<Payment> byBooking(UUID bookingId) {
        return repository.findByBookingId(bookingId);
    }
}
