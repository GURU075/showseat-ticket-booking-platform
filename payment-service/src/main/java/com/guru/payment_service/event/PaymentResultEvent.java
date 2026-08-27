package com.guru.payment_service.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResultEvent(int version, UUID eventId, String eventType, UUID paymentId, UUID bookingId,
                                 Long userId, BigDecimal amount, String currency, String failureReason,
                                 Instant occurredAt) {}
