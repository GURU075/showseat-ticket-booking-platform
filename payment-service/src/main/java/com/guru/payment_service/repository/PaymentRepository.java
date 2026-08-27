package com.guru.payment_service.repository;

import com.guru.payment_service.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByBookingId(UUID bookingId);
    Optional<Payment> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
    Optional<Payment> findByProviderReference(String providerReference);
    Optional<Payment> findByProviderEventId(String providerEventId);
}
