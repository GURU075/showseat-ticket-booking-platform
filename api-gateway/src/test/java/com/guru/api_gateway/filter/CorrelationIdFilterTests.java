package com.guru.api_gateway.filter;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTests {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void makesTheCorrelationIdAvailableDuringTheRequestAndCleansItAfterward()
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/event/getAll");
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> downstreamHeader = new AtomicReference<>();
        AtomicReference<String> mdcDuringRequest = new AtomicReference<>();

        this.filter.doFilter(request, response, (wrappedRequest, wrappedResponse) -> {
            downstreamHeader.set(((HttpServletRequest) wrappedRequest)
                    .getHeader(CorrelationIdFilter.HEADER_NAME));
            mdcDuringRequest.set(MDC.get(CorrelationIdFilter.MDC_KEY));
        });

        assertThat(downstreamHeader).hasValue("request-123");
        assertThat(mdcDuringRequest).hasValue("request-123");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo("request-123");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void restoresAnOuterMdcValueAfterTheRequest() throws Exception {
        MDC.put(CorrelationIdFilter.MDC_KEY, "outer-context");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        this.filter.doFilter(request, response, (wrappedRequest, wrappedResponse) ->
                assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNotEqualTo("outer-context")
        );

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo("outer-context");
    }
}
