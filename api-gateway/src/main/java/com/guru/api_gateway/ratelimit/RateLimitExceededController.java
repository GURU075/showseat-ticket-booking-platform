package com.guru.api_gateway.ratelimit;

import com.guru.api_gateway.filter.CorrelationIdFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
final class RateLimitExceededController {

    @RequestMapping(GatewayRateLimitFilter.FALLBACK_PATH)
    ResponseEntity<ProblemDetail> rateLimitExceeded(HttpServletRequest request) {
        if (request.getDispatcherType() != DispatcherType.FORWARD) {
            return ResponseEntity.notFound().build();
        }

        String serviceName = requiredAttribute(
                request,
                GatewayRateLimitFilter.SERVICE_NAME_ATTRIBUTE,
                String.class
        );
        Long retryAfter = requiredAttribute(
                request,
                GatewayRateLimitFilter.RETRY_AFTER_ATTRIBUTE,
                Long.class
        );
        Long limit = requiredAttribute(
                request,
                GatewayRateLimitFilter.LIMIT_ATTRIBUTE,
                Long.class
        );
        String originalPath = originalRequestPath(request);
        String correlationId = request.getHeader(CorrelationIdFilter.HEADER_NAME);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too many requests were sent to " + serviceName
                        + ". Please retry after " + retryAfter + " seconds."
        );
        problem.setType(URI.create("urn:showseat:problem:rate-limit-exceeded"));
        problem.setTitle("Rate Limit Exceeded");
        problem.setProperty("code", "RATE_LIMIT_EXCEEDED");
        problem.setProperty("path", originalPath);
        problem.setProperty("correlationId", correlationId);
        problem.setProperty("limit", limit);
        problem.setProperty("remaining", 0);

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .cacheControl(CacheControl.noStore())
                .header("Retry-After", retryAfter.toString())
                .header("X-RateLimit-Limit", limit.toString())
                .header("X-RateLimit-Remaining", "0")
                .body(problem);
    }

    private static String originalRequestPath(HttpServletRequest request) {
        Object forwardedRequestUri = request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
        return forwardedRequestUri instanceof String path ? path : request.getRequestURI();
    }

    private static <T> T requiredAttribute(
            HttpServletRequest request,
            String name,
            Class<T> type
    ) {
        return type.cast(request.getAttribute(name));
    }
}
