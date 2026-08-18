package com.guru.api_gateway.fallback;

import com.guru.api_gateway.filter.CorrelationIdFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Duration;

@RestController
final class DownstreamServiceFallbackController {

    private static final Logger log =
            LoggerFactory.getLogger(DownstreamServiceFallbackController.class);

    private static final ServiceDefinition EVENT_SERVICE = new ServiceDefinition(
            "Event Service",
            "event-service-unavailable",
            "EVENT_SERVICE_UNAVAILABLE"
    );
    private static final ServiceDefinition VENUE_SERVICE = new ServiceDefinition(
            "Venue Service",
            "venue-service-unavailable",
            "VENUE_SERVICE_UNAVAILABLE"
    );
    private static final ServiceDefinition SHOW_SERVICE = new ServiceDefinition(
            "Show Service",
            "show-service-unavailable",
            "SHOW_SERVICE_UNAVAILABLE"
    );
    private static final ServiceDefinition SEAT_INVENTORY_SERVICE = new ServiceDefinition(
            "Seat Inventory Service",
            "seat-inventory-service-unavailable",
            "SEAT_INVENTORY_SERVICE_UNAVAILABLE"
    );

    private final long eventRetryAfterSeconds;
    private final long venueRetryAfterSeconds;
    private final long showRetryAfterSeconds;
    private final long seatInventoryRetryAfterSeconds;

    DownstreamServiceFallbackController(
            @Value("${EVENT_CIRCUIT_BREAKER_OPEN_WAIT:10s}") Duration eventOpenStateWait,
            @Value("${VENUE_CIRCUIT_BREAKER_OPEN_WAIT:10s}") Duration venueOpenStateWait,
            @Value("${SHOW_CIRCUIT_BREAKER_OPEN_WAIT:10s}") Duration showOpenStateWait,
            @Value("${SEAT_INVENTORY_CIRCUIT_BREAKER_OPEN_WAIT:10s}")
            Duration seatInventoryOpenStateWait
    ) {
        this.eventRetryAfterSeconds = toRetryAfterSeconds(eventOpenStateWait);
        this.venueRetryAfterSeconds = toRetryAfterSeconds(venueOpenStateWait);
        this.showRetryAfterSeconds = toRetryAfterSeconds(showOpenStateWait);
        this.seatInventoryRetryAfterSeconds =
                toRetryAfterSeconds(seatInventoryOpenStateWait);
    }

    @RequestMapping("/internal/fallback/event-service")
    ResponseEntity<ProblemDetail> eventServiceUnavailable(HttpServletRequest request) {
        return serviceUnavailable(request, EVENT_SERVICE, this.eventRetryAfterSeconds);
    }

    @RequestMapping("/internal/fallback/venue-service")
    ResponseEntity<ProblemDetail> venueServiceUnavailable(HttpServletRequest request) {
        return serviceUnavailable(request, VENUE_SERVICE, this.venueRetryAfterSeconds);
    }

    @RequestMapping("/internal/fallback/show-service")
    ResponseEntity<ProblemDetail> showServiceUnavailable(HttpServletRequest request) {
        return serviceUnavailable(request, SHOW_SERVICE, this.showRetryAfterSeconds);
    }

    @RequestMapping("/internal/fallback/seat-inventory-service")
    ResponseEntity<ProblemDetail> seatInventoryServiceUnavailable(
            HttpServletRequest request
    ) {
        return serviceUnavailable(
                request,
                SEAT_INVENTORY_SERVICE,
                this.seatInventoryRetryAfterSeconds
        );
    }

    private static ResponseEntity<ProblemDetail> serviceUnavailable(
            HttpServletRequest request,
            ServiceDefinition service,
            long retryAfterSeconds
    ) {
        if (request.getDispatcherType() != DispatcherType.FORWARD) {
            return ResponseEntity.notFound().build();
        }

        String originalPath = originalRequestPath(request);
        String correlationId = request.getHeader(CorrelationIdFilter.HEADER_NAME);

        log.warn(
                "{} fallback activated method={} path={}",
                service.displayName(),
                request.getMethod(),
                originalPath
        );

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                service.displayName()
                        + " is temporarily unavailable. Please try again later."
        );
        problem.setType(URI.create("urn:showseat:problem:" + service.problemType()));
        problem.setTitle(service.displayName() + " Unavailable");
        problem.setProperty("code", service.errorCode());
        problem.setProperty("path", originalPath);
        problem.setProperty("correlationId", correlationId);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .cacheControl(CacheControl.noStore())
                .header("Retry-After", Long.toString(retryAfterSeconds))
                .body(problem);
    }

    private static long toRetryAfterSeconds(Duration openStateWait) {
        return Math.max(1, openStateWait.toSeconds());
    }

    private static String originalRequestPath(HttpServletRequest request) {
        Object forwardedRequestUri = request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
        return forwardedRequestUri instanceof String path ? path : request.getRequestURI();
    }

    private record ServiceDefinition(String displayName, String problemType, String errorCode) {
    }
}
