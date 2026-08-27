package com.guru.payment_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_payment_booking",
                        columnNames = "booking_id"
                ),
                @UniqueConstraint(
                        name = "uk_payment_user_idempotency",
                        columnNames = {"user_id", "idempotency_key"}
                )
        }
)
public class Payment {

    @Id
    private UUID id;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "provider_reference", nullable = false, length = 80, unique = true)
    private String providerReference;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "provider_event_id", length = 128, unique = true)
    private String providerEventId;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() {
        // Required by JPA.
    }

    public static Payment pending(
            UUID bookingId,
            Long userId,
            BigDecimal amount,
            String currency,
            String idempotencyKey,
            String requestHash,
            Instant now
    ) {
        Payment payment = new Payment();
        payment.id = UUID.randomUUID();
        payment.bookingId = bookingId;
        payment.userId = userId;
        payment.amount = amount;
        payment.currency = currency;
        payment.status = PaymentStatus.PENDING;
        payment.providerReference = "sim_" + UUID.randomUUID();
        payment.idempotencyKey = idempotencyKey;
        payment.requestHash = requestHash;
        payment.createdAt = now;
        payment.updatedAt = now;
        return payment;
    }

    /**
     * Applies the first final result. Returning false means that the same final
     * result was already applied and no new Kafka event is needed.
     */
    public boolean applyResult(
            PaymentStatus result,
            String eventId,
            String reason,
            Instant now
    ) {
        if (result == PaymentStatus.PENDING) {
            throw new IllegalArgumentException("Provider result must be terminal");
        }

        if (status != PaymentStatus.PENDING) {
            if (status != result) {
                throw new IllegalStateException(
                        "A terminal payment result cannot be reversed"
                );
            }
            return false;
        }

        status = result;
        providerEventId = eventId;
        failureReason = result == PaymentStatus.FAILED ? reason : null;
        updatedAt = now;
        return true;
    }

    public UUID getId() { return id; }
    public UUID getBookingId() { return bookingId; }
    public Long getUserId() { return userId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public String getProviderReference() { return providerReference; }
    public String getRequestHash() { return requestHash; }
    public String getProviderEventId() { return providerEventId; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
