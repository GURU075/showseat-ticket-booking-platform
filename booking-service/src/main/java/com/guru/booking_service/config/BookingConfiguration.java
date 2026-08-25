package com.guru.booking_service.config;

import feign.RequestInterceptor;
import feign.Retryer;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.ZoneId;

@Configuration(proxyBeanMethods = false)
public class BookingConfiguration {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    @Bean
    Clock bookingClock(@Value("${booking.time-zone:Asia/Kolkata}") String timeZone) {
        return Clock.system(ZoneId.of(timeZone));
    }

    @Bean
    Retryer feignRetryer() {
        // A booking POST must never be repeated invisibly by the HTTP client.
        return Retryer.NEVER_RETRY;
    }

    @Bean
    RequestInterceptor correlationIdForwardingInterceptor() {
        return template -> {
            if (RequestContextHolder.getRequestAttributes()
                    instanceof ServletRequestAttributes attributes) {
                HttpServletRequest request = attributes.getRequest();
                String correlationId = request.getHeader(CORRELATION_ID_HEADER);
                if (correlationId == null || correlationId.isBlank()) {
                    correlationId = MDC.get("correlationId");
                }
                if (correlationId != null && !correlationId.isBlank()) {
                    template.header(CORRELATION_ID_HEADER, correlationId);
                }
            }
        };
    }
}
