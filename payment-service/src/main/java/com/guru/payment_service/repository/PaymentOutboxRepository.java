package com.guru.payment_service.repository;

import com.guru.payment_service.domain.PaymentOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PaymentOutboxRepository extends JpaRepository<PaymentOutboxEvent, UUID> {
    List<PaymentOutboxEvent> findTop50ByPublishedAtIsNullOrderByOccurredAtAsc();
}
