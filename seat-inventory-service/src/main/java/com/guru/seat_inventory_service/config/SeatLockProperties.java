package com.guru.seat_inventory_service.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "seat-lock")
public record SeatLockProperties(
        @NotNull Duration ttl,
        @NotNull Duration confirmationTtl
) {
    public SeatLockProperties {
        if (ttl != null && (ttl.isZero() || ttl.isNegative())) {
            throw new IllegalArgumentException("seat-lock.ttl must be positive");
        }
        if (confirmationTtl != null && (confirmationTtl.isZero() || confirmationTtl.isNegative())) {
            throw new IllegalArgumentException("seat-lock.confirmation-ttl must be positive");
        }
    }
}
