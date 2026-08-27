package com.guru.booking_service.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentResultListener {
    private final ObjectMapper objectMapper;
    private final PaymentResultHandler handler;
    public PaymentResultListener(ObjectMapper objectMapper, PaymentResultHandler handler) {
        this.objectMapper = objectMapper; this.handler = handler;
    }

    @KafkaListener(topics = "${booking.payment-events-topic}")
    public void onPaymentResult(String payload) {
        try { handler.handle(objectMapper.readValue(payload, PaymentResultEvent.class)); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("Invalid payment event JSON", exception); }
    }
}
