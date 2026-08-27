package com.guru.booking_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@FeignClient(name = "payment-service")
public interface PaymentClient {
    @PostMapping("/api/v1/payments")
    PaymentDetails create(@RequestHeader("Idempotency-Key") String key, @RequestBody CreatePayment request);

    record CreatePayment(UUID bookingId, Long userId, BigDecimal amount, String currency) {}
    record PaymentDetails(UUID id, UUID bookingId, Long userId, BigDecimal amount, String currency,
                          String status, String providerReference, String checkoutUrl,
                          String failureReason, Instant createdAt, Instant updatedAt) {}
}
