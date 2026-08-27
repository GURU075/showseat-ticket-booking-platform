package com.guru.payment_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guru.payment_service.api.CreatePaymentRequest;
import com.guru.payment_service.api.PaymentResponse;
import com.guru.payment_service.api.ProviderPaymentEventRequest;
import com.guru.payment_service.domain.Payment;
import com.guru.payment_service.domain.PaymentOutboxEvent;
import com.guru.payment_service.domain.PaymentStatus;
import com.guru.payment_service.event.PaymentResultEvent;
import com.guru.payment_service.exception.PaymentException;
import com.guru.payment_service.repository.PaymentOutboxRepository;
import com.guru.payment_service.repository.PaymentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class PaymentService {

    private static final int EVENT_VERSION = 1;
    private static final String PAYMENT_SUCCEEDED = "PAYMENT_SUCCEEDED";
    private static final String PAYMENT_FAILED = "PAYMENT_FAILED";

    private final PaymentPersistenceService persistence;
    private final PaymentRepository payments;
    private final PaymentOutboxRepository outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PaymentService(
            PaymentPersistenceService persistence,
            PaymentRepository payments,
            PaymentOutboxRepository outbox,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.persistence = persistence;
        this.payments = payments;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Creates the single payment associated with a booking.
     * A retry with the same key and data returns the existing payment.
     */
    public CreationResult create(CreatePaymentRequest request, String idempotencyKey) {
        BigDecimal amount = normalizeAmount(request.amount());
        String currency = normalizeCurrency(request.currency());
        String requestHash = paymentRequestHash(request, amount, currency);

        try {
            Payment payment = persistence.reserve(
                    request.bookingId(),
                    request.userId(),
                    amount,
                    currency,
                    idempotencyKey,
                    requestHash
            );
            return created(payment);
        }
        catch (DataIntegrityViolationException duplicatePayment) {
            return replayExistingPayment(request, idempotencyKey, requestHash);
        }
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(UUID paymentId) {
        Payment payment = payments.findById(paymentId)
                .orElseThrow(() -> new PaymentException(
                        HttpStatus.NOT_FOUND,
                        "PAYMENT_NOT_FOUND",
                        "Payment " + paymentId + " was not found"
                ));
        return PaymentResponse.from(payment);
    }

    /**
     * Applies a terminal result received from a provider.
     * The payment update and outbox insert commit in the same database transaction.
     */
    @Transactional
    public PaymentResponse applyProviderEvent(ProviderPaymentEventRequest request) {
        Payment previouslyProcessed = payments.findByProviderEventId(request.providerEventId())
                .orElse(null);
        if (previouslyProcessed != null) {
            return PaymentResponse.from(previouslyProcessed);
        }

        validateProviderResult(request);
        Payment payment = findByProviderReference(request.providerReference());
        Instant occurredAt = Instant.now(clock);

        boolean paymentChanged = applyResult(payment, request, occurredAt);
        if (paymentChanged) {
            payments.save(payment);
            storePaymentResultEvent(payment, occurredAt);
        }

        return PaymentResponse.from(payment);
    }

    private CreationResult replayExistingPayment(
            CreatePaymentRequest request,
            String idempotencyKey,
            String requestHash
    ) {
        Payment existingPayment = persistence.byKey(request.userId(), idempotencyKey)
                .or(() -> persistence.byBooking(request.bookingId()))
                .orElseThrow(() -> conflict(
                        "PAYMENT_CREATION_RACE",
                        "Another payment creation is in progress"
                ));

        if (!existingPayment.getRequestHash().equals(requestHash)) {
            throw conflict(
                    "PAYMENT_REQUEST_CONFLICT",
                    "The booking or idempotency key already has different payment data"
            );
        }

        return new CreationResult(PaymentResponse.from(existingPayment), true);
    }

    private void validateProviderResult(ProviderPaymentEventRequest request) {
        if (request.status() == PaymentStatus.PENDING) {
            throw new PaymentException(
                    HttpStatus.BAD_REQUEST,
                    "NON_TERMINAL_PROVIDER_EVENT",
                    "Provider result must be SUCCEEDED or FAILED"
            );
        }

        boolean missingFailureReason = request.failureReason() == null
                || request.failureReason().isBlank();
        if (request.status() == PaymentStatus.FAILED && missingFailureReason) {
            throw new PaymentException(
                    HttpStatus.BAD_REQUEST,
                    "FAILURE_REASON_REQUIRED",
                    "A failed payment needs a failure reason"
            );
        }
    }

    private Payment findByProviderReference(String providerReference) {
        return payments.findByProviderReference(providerReference)
                .orElseThrow(() -> new PaymentException(
                        HttpStatus.NOT_FOUND,
                        "PAYMENT_NOT_FOUND",
                        "Provider payment reference was not found"
                ));
    }

    private boolean applyResult(
            Payment payment,
            ProviderPaymentEventRequest request,
            Instant occurredAt
    ) {
        try {
            return payment.applyResult(
                    request.status(),
                    request.providerEventId(),
                    request.failureReason(),
                    occurredAt
            );
        }
        catch (IllegalStateException invalidTransition) {
            throw conflict("PAYMENT_RESULT_CONFLICT", invalidTransition.getMessage());
        }
    }

    private void storePaymentResultEvent(Payment payment, Instant occurredAt) {
        UUID eventId = UUID.randomUUID();
        String eventType = eventTypeFor(payment.getStatus());
        PaymentResultEvent event = new PaymentResultEvent(
                EVENT_VERSION,
                eventId,
                eventType,
                payment.getId(),
                payment.getBookingId(),
                payment.getUserId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getFailureReason(),
                occurredAt
        );

        PaymentOutboxEvent outboxEvent = new PaymentOutboxEvent(
                eventId,
                payment.getId(),
                eventType,
                toJson(event),
                occurredAt
        );
        outbox.save(outboxEvent);
    }

    private String paymentRequestHash(
            CreatePaymentRequest request,
            BigDecimal amount,
            String currency
    ) {
        String canonicalRequest = request.bookingId()
                + "|" + request.userId()
                + "|" + amount.toPlainString()
                + "|" + currency;
        return sha256(canonicalRequest);
    }

    private String toJson(PaymentResultEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        }
        catch (JsonProcessingException serializationFailure) {
            throw new IllegalStateException(
                    "Could not serialize payment event",
                    serializationFailure
            );
        }
    }

    private static BigDecimal normalizeAmount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static String normalizeCurrency(String currency) {
        return currency.toUpperCase(Locale.ROOT);
    }

    private static String eventTypeFor(PaymentStatus status) {
        return status == PaymentStatus.SUCCEEDED ? PAYMENT_SUCCEEDED : PAYMENT_FAILED;
    }

    private static CreationResult created(Payment payment) {
        return new CreationResult(PaymentResponse.from(payment), false);
    }

    private static PaymentException conflict(String code, String message) {
        return new PaymentException(HttpStatus.CONFLICT, code, message);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        }
        catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public record CreationResult(PaymentResponse payment, boolean replayed) {
    }
}
