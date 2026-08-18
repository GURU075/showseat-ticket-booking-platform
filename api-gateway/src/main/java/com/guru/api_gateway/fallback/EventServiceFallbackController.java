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
final class EventServiceFallbackController {

    private static final Logger log = LoggerFactory.getLogger(EventServiceFallbackController.class);
    private static final String FALLBACK_PATH = "/internal/fallback/event-service";
    private final long retryAfterSeconds;

    EventServiceFallbackController(
            @Value("${EVENT_CIRCUIT_BREAKER_OPEN_WAIT:10s}") Duration openStateWait
    ) {
        this.retryAfterSeconds = Math.max(1, openStateWait.toSeconds());
    }

    @RequestMapping(FALLBACK_PATH)
    ResponseEntity<ProblemDetail> eventServiceUnavailable(HttpServletRequest request) {
        if (request.getDispatcherType() != DispatcherType.FORWARD) {
            return ResponseEntity.notFound().build();
        }

        String originalPath = originalRequestPath(request);
        String correlationId = request.getHeader(CorrelationIdFilter.HEADER_NAME);

        log.warn(
                "Event Service fallback activated method={} path={}",
                request.getMethod(),
                originalPath
        );

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Event Service is temporarily unavailable. Please try again later."
        );
        problem.setType(URI.create("urn:showseat:problem:event-service-unavailable"));
        problem.setTitle("Event Service Unavailable");
        problem.setProperty("code", "EVENT_SERVICE_UNAVAILABLE");
        problem.setProperty("path", originalPath);
        problem.setProperty("correlationId", correlationId);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .cacheControl(CacheControl.noStore())
                .header("Retry-After", Long.toString(this.retryAfterSeconds))
                .body(problem);
    }

    private static String originalRequestPath(HttpServletRequest request) {
        Object forwardedRequestUri = request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
        return forwardedRequestUri instanceof String path ? path : request.getRequestURI();
    }
}
