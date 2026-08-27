package com.guru.payment_service.api;

import com.guru.payment_service.domain.PaymentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SimulatePaymentRequest(@NotNull PaymentStatus status, @Size(max = 500) String failureReason) {}
