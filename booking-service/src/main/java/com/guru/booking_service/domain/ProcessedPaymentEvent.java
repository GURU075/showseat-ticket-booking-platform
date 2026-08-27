package com.guru.booking_service.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_payment_events")
public class ProcessedPaymentEvent {
    @Id @Column(name = "event_id") private UUID eventId;
    @Column(name = "payment_id", nullable = false) private UUID paymentId;
    @Column(name = "booking_id", nullable = false) private UUID bookingId;
    @Column(name = "event_type", nullable = false, length = 40) private String eventType;
    @Column(name = "processed_at", nullable = false) private Instant processedAt;
    protected ProcessedPaymentEvent() {}
    public ProcessedPaymentEvent(UUID eventId, UUID paymentId, UUID bookingId, String eventType, Instant processedAt) {
        this.eventId = eventId; this.paymentId = paymentId; this.bookingId = bookingId;
        this.eventType = eventType; this.processedAt = processedAt;
    }
}
