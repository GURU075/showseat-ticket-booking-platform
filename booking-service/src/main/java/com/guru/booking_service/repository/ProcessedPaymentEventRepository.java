package com.guru.booking_service.repository;

import com.guru.booking_service.domain.ProcessedPaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ProcessedPaymentEventRepository extends JpaRepository<ProcessedPaymentEvent, UUID> {}
