package com.guru.payment_service.api;

import com.guru.payment_service.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/payment-provider/events")
public class PaymentProviderController {

    private final PaymentService paymentService;

    public PaymentProviderController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** Receives a callback from a real payment-provider adapter. */
    @PostMapping
    public PaymentResponse receive(
            @Valid @RequestBody ProviderPaymentEventRequest providerEvent
    ) {
        return paymentService.applyProviderEvent(providerEvent);
    }

    /** Development-only shortcut that behaves like a provider callback. */
    @PostMapping("/simulator/{providerReference}")
    public PaymentResponse simulate(
            @PathVariable String providerReference,
            @Valid @RequestBody SimulatePaymentRequest simulation
    ) {
        ProviderPaymentEventRequest simulatedProviderEvent =
                new ProviderPaymentEventRequest(
                        "simulator-" + UUID.randomUUID(),
                        providerReference,
                        simulation.status(),
                        simulation.failureReason()
                );

        return paymentService.applyProviderEvent(simulatedProviderEvent);
    }
}
