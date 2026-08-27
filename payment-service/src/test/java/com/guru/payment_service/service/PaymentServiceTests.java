package com.guru.payment_service.service;

import com.guru.payment_service.api.*;
import com.guru.payment_service.domain.PaymentStatus;
import com.guru.payment_service.repository.PaymentOutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PaymentServiceTests {
    @Autowired PaymentService service;
    @Autowired PaymentOutboxRepository outbox;

    @Test
    void createsIdempotentlyAndStoresOneDurableTerminalEvent() {
        UUID bookingId = UUID.randomUUID();
        CreatePaymentRequest request = new CreatePaymentRequest(bookingId, 10L, new BigDecimal("500.00"), "INR");
        long before = outbox.count();

        PaymentService.CreationResult first = service.create(request, "pay-" + bookingId);
        PaymentService.CreationResult replay = service.create(request, "pay-" + bookingId);
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.payment().id()).isEqualTo(first.payment().id());

        ProviderPaymentEventRequest event = new ProviderPaymentEventRequest(
                "provider-event-" + bookingId, first.payment().providerReference(), PaymentStatus.SUCCEEDED, null);
        PaymentResponse succeeded = service.applyProviderEvent(event);
        PaymentResponse duplicate = service.applyProviderEvent(event);

        assertThat(succeeded.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(duplicate.id()).isEqualTo(succeeded.id());
        assertThat(outbox.count()).isEqualTo(before + 1);
    }
}
