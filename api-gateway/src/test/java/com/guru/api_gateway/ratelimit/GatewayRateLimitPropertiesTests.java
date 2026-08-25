package com.guru.api_gateway.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayRateLimitPropertiesTests {

    private static final GatewayRateLimitProperties.Policy VALID_POLICY =
            new GatewayRateLimitProperties.Policy(10, 10, Duration.ofMinutes(1));

    @Test
    void rejectsNonPositiveCacheSize() {
        assertThatThrownBy(() -> properties(0, Duration.ofMinutes(10), allPolicies()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-buckets");
    }

    @Test
    void rejectsNonPositiveBucketExpiration() {
        assertThatThrownBy(() -> properties(100, Duration.ZERO, allPolicies()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bucket-expiration");
    }

    @Test
    void requiresAPolicyForEveryRoutedService() {
        assertThatThrownBy(() -> properties(
                100,
                Duration.ofMinutes(10),
                Map.of("event-service", VALID_POLICY)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("policies are required");
    }

    @Test
    void rejectsInvalidTokenBucketValues() {
        assertThatThrownBy(() -> new GatewayRateLimitProperties.Policy(
                10,
                0,
                Duration.ofMinutes(1)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("refill-tokens");
    }

    private static GatewayRateLimitProperties properties(
            int maxBuckets,
            Duration expiration,
            Map<String, GatewayRateLimitProperties.Policy> policies
    ) {
        return new GatewayRateLimitProperties(true, maxBuckets, expiration, policies);
    }

    private static Map<String, GatewayRateLimitProperties.Policy> allPolicies() {
        return Map.of(
                "event-service", VALID_POLICY,
                "venue-service", VALID_POLICY,
                "show-service", VALID_POLICY,
                "seat-inventory-service", VALID_POLICY,
                "booking-service", VALID_POLICY
        );
    }
}
