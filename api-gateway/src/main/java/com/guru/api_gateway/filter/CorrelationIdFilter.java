package com.guru.api_gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);
    private static final Pattern SAFE_CORRELATION_ID =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request.getHeader(HEADER_NAME));
        String previousCorrelationId = MDC.get(MDC_KEY);
        HttpServletRequest requestWithCorrelationId =
                new CorrelationIdRequestWrapper(request, correlationId);

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER_NAME, correlationId);

        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(requestWithCorrelationId, response);
        }
        finally {
            long durationMillis = (System.nanoTime() - startedAt) / 1_000_000;
            log.info(
                    "Completed gateway request method={} path={} status={} durationMs={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    durationMillis
            );
            restoreMdc(previousCorrelationId);
        }
    }

    private static String resolveCorrelationId(String candidate) {
        if (candidate != null) {
            String trimmedCandidate = candidate.trim();
            if (SAFE_CORRELATION_ID.matcher(trimmedCandidate).matches()) {
                return trimmedCandidate;
            }
        }
        return UUID.randomUUID().toString();
    }

    private static void restoreMdc(String previousCorrelationId) {
        if (previousCorrelationId == null) {
            MDC.remove(MDC_KEY);
        }
        else {
            MDC.put(MDC_KEY, previousCorrelationId);
        }
    }

    private static final class CorrelationIdRequestWrapper extends HttpServletRequestWrapper {

        private final String correlationId;

        private CorrelationIdRequestWrapper(HttpServletRequest request, String correlationId) {
            super(request);
            this.correlationId = correlationId;
        }

        @Override
        public String getHeader(String name) {
            if (HEADER_NAME.equalsIgnoreCase(name)) {
                return this.correlationId;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (HEADER_NAME.equalsIgnoreCase(name)) {
                return Collections.enumeration(Collections.singleton(this.correlationId));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> headerNames = new LinkedHashSet<>();
            Enumeration<String> existingHeaderNames = super.getHeaderNames();
            if (existingHeaderNames != null) {
                existingHeaderNames.asIterator().forEachRemaining(headerNames::add);
            }
            headerNames.removeIf(HEADER_NAME::equalsIgnoreCase);
            headerNames.add(HEADER_NAME);
            return Collections.enumeration(headerNames);
        }
    }
}
