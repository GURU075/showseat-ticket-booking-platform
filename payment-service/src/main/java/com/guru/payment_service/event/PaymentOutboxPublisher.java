package com.guru.payment_service.event;

import com.guru.payment_service.domain.PaymentOutboxEvent;
import com.guru.payment_service.repository.PaymentOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(
        name = "payment.outbox.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class PaymentOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentOutboxPublisher.class);

    private final PaymentOutboxRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final String topic;
    private final Clock clock;

    public PaymentOutboxPublisher(
            PaymentOutboxRepository repository,
            KafkaTemplate<String, String> kafka,
            @Value("${payment.events-topic}") String topic,
            Clock clock
    ) {
        this.repository = repository;
        this.kafka = kafka;
        this.topic = topic;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${payment.outbox.publish-delay-ms:1000}")
    @Transactional
    public void publishPending() {
        List<PaymentOutboxEvent> pendingEvents =
                repository.findTop50ByPublishedAtIsNullOrderByOccurredAtAsc();

        for (PaymentOutboxEvent event : pendingEvents) {
            if (!publish(event)) {
                break;
            }
        }
    }

    private boolean publish(PaymentOutboxEvent event) {
        try {
            String messageKey = event.getAggregateId().toString();
            kafka.send(topic, messageKey, event.getPayload())
                    .get(5, TimeUnit.SECONDS);
            event.published(Instant.now(clock));
            return true;
        }
        catch (Exception failure) {
            log.warn(
                    "Payment event {} remains in the outbox because Kafka publish failed",
                    event.getEventId(),
                    failure
            );
            return false;
        }
    }
}
