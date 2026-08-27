package com.guru.booking_service.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "bookings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_booking_user_idempotency",
                columnNames = {"user_id", "idempotency_key"}
        )
)
public class Booking {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "show_id", nullable = false)
    private Long showId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "booking_seats", joinColumns = @JoinColumn(name = "booking_id"))
    @OrderColumn(name = "seat_order")
    @Column(name = "seat_number", nullable = false, length = 20)
    private List<String> seatNumbers = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private BookingStatus status;

    @Column(name = "total_amount", precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(length = 3)
    private String currency;

    @Column(name = "lock_id", length = 80)
    private String lockId;

    @Column(name = "lock_expires_at")
    private Instant lockExpiresAt;

    @Column(name = "payment_id", unique = true)
    private UUID paymentId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Booking() {
    }

    public static Booking creating(
            Long userId,
            Long showId,
            List<String> seatNumbers,
            String idempotencyKey,
            String requestHash,
            Instant now
    ) {
        Booking booking = new Booking();
        booking.id = UUID.randomUUID();
        booking.userId = userId;
        booking.showId = showId;
        booking.seatNumbers = new ArrayList<>(seatNumbers);
        booking.status = BookingStatus.CREATING;
        booking.idempotencyKey = idempotencyKey;
        booking.requestHash = requestHash;
        booking.createdAt = now;
        booking.updatedAt = now;
        return booking;
    }

    public void markPending(
            BigDecimal totalAmount,
            String currency,
            String lockId,
            Instant lockExpiresAt,
            Instant now
    ) {
        if (status != BookingStatus.CREATING) {
            throw new IllegalStateException("Only a creating booking can become pending");
        }
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.lockId = lockId;
        this.lockExpiresAt = lockExpiresAt;
        this.status = BookingStatus.PENDING;
        this.updatedAt = now;
    }

    public void confirm(UUID paymentId, Instant now) {
        if (status == BookingStatus.CONFIRMED && paymentId.equals(this.paymentId)) return;
        if (status != BookingStatus.PENDING) throw new IllegalStateException("Only a pending booking can be confirmed");
        this.paymentId = paymentId;
        this.status = BookingStatus.CONFIRMED;
        this.updatedAt = now;
    }

    public void cancel(UUID paymentId, Instant now) {
        if (status == BookingStatus.CANCELLED && paymentId.equals(this.paymentId)) return;
        if (status != BookingStatus.PENDING) throw new IllegalStateException("Only a pending booking can be cancelled");
        this.paymentId = paymentId;
        this.status = BookingStatus.CANCELLED;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getShowId() { return showId; }
    public List<String> getSeatNumbers() { return List.copyOf(seatNumbers); }
    public BookingStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCurrency() { return currency; }
    public String getLockId() { return lockId; }
    public Instant getLockExpiresAt() { return lockExpiresAt; }
    public UUID getPaymentId() { return paymentId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
