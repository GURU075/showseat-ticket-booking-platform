package com.guru.api_gateway.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

@ConfigurationProperties("gateway.rate-limit")
public record GatewayRateLimitProperties(
        boolean enabled,
        int maxBuckets,
        Duration bucketExpiration,
        Map<String, Policy> policies
) {

    private static final Set<String> REQUIRED_POLICIES = Set.of(
            "event-service",
            "venue-service",
            "show-service",
            "seat-inventory-service",
            "booking-service"
    );

    public GatewayRateLimitProperties {
        policies = policies == null ? Map.of() : Map.copyOf(policies);

        if (maxBuckets <= 0) {
            throw new IllegalArgumentException("Rate-limit max-buckets must be positive");
        }
        if (bucketExpiration == null || bucketExpiration.isZero()
                || bucketExpiration.isNegative()) {
            throw new IllegalArgumentException(
                    "Rate-limit bucket-expiration must be positive"
            );
        }
        if (!policies.keySet().containsAll(REQUIRED_POLICIES)) {
            throw new IllegalArgumentException(
                    "Rate-limit policies are required for " + REQUIRED_POLICIES
            );
        }
    }

    public Policy policyFor(String serviceId) {
        Policy policy = this.policies.get(serviceId);
        if (policy == null) {
            throw new IllegalArgumentException("Unknown rate-limit policy: " + serviceId);
        }
        return policy;
    }

    public record Policy(long capacity, long refillTokens, Duration refillPeriod) {

        public Policy {
            if (capacity <= 0) {
                throw new IllegalArgumentException("Rate-limit capacity must be positive");
            }
            if (refillTokens <= 0) {
                throw new IllegalArgumentException("Rate-limit refill-tokens must be positive");
            }
            if (refillPeriod == null || refillPeriod.isZero() || refillPeriod.isNegative()) {
                throw new IllegalArgumentException(
                        "Rate-limit refill-period must be positive"
                );
            }
        }
    }
}
