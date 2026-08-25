package com.guru.api_gateway.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.Principal;
import java.time.Duration;
import java.util.List;

final class GatewayRateLimitFilter extends OncePerRequestFilter {

    static final String FALLBACK_PATH = "/internal/rate-limit-exceeded";
    static final String SERVICE_NAME_ATTRIBUTE =
            GatewayRateLimitFilter.class.getName() + ".serviceName";
    static final String RETRY_AFTER_ATTRIBUTE =
            GatewayRateLimitFilter.class.getName() + ".retryAfter";
    static final String LIMIT_ATTRIBUTE =
            GatewayRateLimitFilter.class.getName() + ".limit";

    private static final Logger log = LoggerFactory.getLogger(GatewayRateLimitFilter.class);
    private static final String LIMIT_HEADER = "X-RateLimit-Limit";
    private static final String REMAINING_HEADER = "X-RateLimit-Remaining";

    private static final List<RoutePolicy> ROUTE_POLICIES = List.of(
            new RoutePolicy("event-service", "Event Service", List.of("/api/event")),
            new RoutePolicy(
                    "venue-service",
                    "Venue Service",
                    List.of(
                            "/api/v1/cities",
                            "/api/v1/venues",
                            "/api/v1/screens",
                            "/api/v1/venue-seats"
                    )
            ),
            new RoutePolicy("show-service", "Show Service", List.of("/api/v1/shows")),
            new RoutePolicy(
                    "seat-inventory-service",
                    "Seat Inventory Service",
                    List.of("/api/v1/show-seats")
            ),
            new RoutePolicy("booking-service", "Booking Service", List.of("/api/v1/bookings"))
    );

    private final GatewayRateLimitProperties properties;
    private final Cache<String, Bucket> buckets;

    GatewayRateLimitFilter(
            GatewayRateLimitProperties properties,
            Cache<String, Bucket> buckets
    ) {
        this.properties = properties;
        this.buckets = buckets;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        RoutePolicy route = findRoute(request.getRequestURI());
        if (!this.properties.enabled() || route == null || isCorsPreflight(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        GatewayRateLimitProperties.Policy policy =
                this.properties.policyFor(route.serviceId());
        String clientKey = resolveClientKey(request);
        String bucketKey = route.serviceId() + ':' + clientKey;
        Bucket bucket = this.buckets.get(bucketKey, ignored -> createBucket(policy));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        response.setHeader(LIMIT_HEADER, Long.toString(policy.capacity()));
        response.setHeader(REMAINING_HEADER, Long.toString(probe.getRemainingTokens()));

        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = retryAfterSeconds(probe.getNanosToWaitForRefill());
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        response.setHeader("Cache-Control", "no-store");
        request.setAttribute(SERVICE_NAME_ATTRIBUTE, route.displayName());
        request.setAttribute(RETRY_AFTER_ATTRIBUTE, retryAfterSeconds);
        request.setAttribute(LIMIT_ATTRIBUTE, policy.capacity());

        log.warn(
                "Gateway rate limit exceeded service={} method={} path={}",
                route.serviceId(),
                request.getMethod(),
                request.getRequestURI()
        );
        RequestDispatcher dispatcher = request.getRequestDispatcher(FALLBACK_PATH);
        dispatcher.forward(request, response);
    }

    private static Bucket createBucket(GatewayRateLimitProperties.Policy policy) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(policy.capacity())
                .refillGreedy(policy.refillTokens(), policy.refillPeriod())
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private static RoutePolicy findRoute(String requestPath) {
        return ROUTE_POLICIES.stream()
                .filter(route -> route.matches(requestPath))
                .findFirst()
                .orElse(null);
    }

    private static String resolveClientKey(HttpServletRequest request) {
        Principal principal = request.getUserPrincipal();
        if (principal != null && principal.getName() != null
                && !principal.getName().isBlank()) {
            return "principal:" + principal.getName();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private static boolean isCorsPreflight(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                && request.getHeader("Origin") != null
                && request.getHeader("Access-Control-Request-Method") != null;
    }

    private static long retryAfterSeconds(long nanosToWait) {
        long oneSecondNanos = Duration.ofSeconds(1).toNanos();
        return Math.max(1, (nanosToWait + oneSecondNanos - 1) / oneSecondNanos);
    }

    private record RoutePolicy(
            String serviceId,
            String displayName,
            List<String> pathPrefixes
    ) {

        private boolean matches(String path) {
            return this.pathPrefixes.stream()
                    .anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + '/'));
        }
    }
}
