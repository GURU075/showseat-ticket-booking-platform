package com.guru.payment_service.api;

import com.guru.payment_service.domain.PaymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProviderPaymentEventRequest(
        @NotBlank @Size(max = 128) String providerEventId,
        @NotBlank @Size(max = 80) String providerReference,
        @NotNull PaymentStatus status,
        @Size(max = 500) String failureReason
) {}
