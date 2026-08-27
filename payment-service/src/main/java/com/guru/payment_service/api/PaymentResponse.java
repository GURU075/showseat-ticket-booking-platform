package com.guru.payment_service.api;

import com.guru.payment_service.domain.Payment;
import com.guru.payment_service.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(UUID id, UUID bookingId, Long userId, BigDecimal amount, String currency,
                              PaymentStatus status, String providerReference, String checkoutUrl,
                              String failureReason, Instant createdAt, Instant updatedAt) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getBookingId(), payment.getUserId(), payment.getAmount(),
                payment.getCurrency(), payment.getStatus(), payment.getProviderReference(),
                "/internal/v1/payment-provider/events/simulator/" + payment.getProviderReference(), payment.getFailureReason(),
                payment.getCreatedAt(), payment.getUpdatedAt());
    }
}
