package com.guru.payment_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration
public class PaymentConfiguration {
    @Bean Clock paymentClock() { return Clock.systemUTC(); }
}
