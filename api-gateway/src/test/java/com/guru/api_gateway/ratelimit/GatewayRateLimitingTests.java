package com.guru.api_gateway.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.guru.api_gateway.ApiGatewayApplication;
import com.guru.api_gateway.filter.CorrelationIdFilter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static com.guru.api_gateway.support.DiscoveryTestSupport.disableEureka;
import static com.guru.api_gateway.support.DiscoveryTestSupport.registerInstance;

@SpringBootTest(
        classes = ApiGatewayApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class GatewayRateLimitingTests {

    private static final HttpServer DOWNSTREAM = startDownstream();
    private static final AtomicInteger EVENT_CALLS = new AtomicInteger();
    private static final AtomicInteger VENUE_CALLS = new AtomicInteger();
    private static final AtomicInteger SHOW_CALLS = new AtomicInteger();

    @LocalServerPort
    private int gatewayPort;

    @Autowired
    private Cache<String, Bucket> gatewayRateLimitBuckets;

    @DynamicPropertySource
    static void configureGateway(DynamicPropertyRegistry registry) {
        disableEureka(registry);
        for (String serviceId : List.of(
                "event-service",
                "venue-service",
                "show-service",
                "seat-inventory-service",
                "booking-service"
        )) {
            registerInstance(registry, serviceId, 0, DOWNSTREAM);
        }
        for (String service : List.of(
                "event-service",
                "venue-service",
                "show-service",
                "seat-inventory-service",
                "booking-service"
        )) {
            registry.add(
                    "gateway.rate-limit.policies." + service + ".capacity",
                    () -> 2
            );
            registry.add(
                    "gateway.rate-limit.policies." + service + ".refill-tokens",
                    () -> 2
            );
            registry.add(
                    "gateway.rate-limit.policies." + service + ".refill-period",
                    () -> "1h"
            );
        }
    }

    @BeforeEach
    void resetRateLimitsAndCounters() {
        this.gatewayRateLimitBuckets.invalidateAll();
        EVENT_CALLS.set(0);
        VENUE_CALLS.set(0);
        SHOW_CALLS.set(0);
    }

    @AfterAll
    static void stopDownstream() {
        DOWNSTREAM.stop(0);
    }

    @Test
    void returnsControlled429AndDoesNotCallDownstreamAfterCapacityIsConsumed()
            throws Exception {
        HttpResponse<String> first = sendGet(
                "/api/event/getAll",
                "rate-limit-test",
                "http://localhost:5173"
        );
        HttpResponse<String> second = sendGet(
                "/api/event/getAll",
                "rate-limit-test",
                "http://localhost:5173"
        );
        HttpResponse<String> rejected = sendGet(
                "/api/event/getAll",
                "rate-limit-test",
                "http://localhost:5173"
        );

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(first.headers().firstValue("X-RateLimit-Limit")).hasValue("2");
        assertThat(first.headers().firstValue("X-RateLimit-Remaining")).hasValue("1");
        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(second.headers().firstValue("X-RateLimit-Remaining")).hasValue("0");

        assertThat(rejected.statusCode()).isEqualTo(429);
        assertThat(rejected.headers().firstValue("Retry-After")).hasValue("1800");
        assertThat(rejected.headers().firstValue("X-RateLimit-Limit")).hasValue("2");
        assertThat(rejected.headers().firstValue("X-RateLimit-Remaining")).hasValue("0");
        assertThat(rejected.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(rejected.headers().firstValue(CorrelationIdFilter.HEADER_NAME))
                .hasValue("rate-limit-test");
        assertThat(rejected.headers().firstValue("Access-Control-Allow-Origin"))
                .hasValue("http://localhost:5173");
        assertThat(rejected.headers().firstValue("Access-Control-Expose-Headers").orElse(""))
                .containsIgnoringCase("Retry-After")
                .containsIgnoringCase("X-RateLimit-Limit")
                .containsIgnoringCase("X-RateLimit-Remaining");
        assertThat(rejected.headers().firstValue("Content-Type").orElse(""))
                .startsWith("application/problem+json");
        assertThat(rejected.body())
                .contains("\"status\":429")
                .contains("\"code\":\"RATE_LIMIT_EXCEEDED\"")
                .contains("\"path\":\"/api/event/getAll\"")
                .contains("\"correlationId\":\"rate-limit-test\"");
        assertThat(EVENT_CALLS).hasValue(2);
    }

    @Test
    void keepsDifferentDownstreamServiceQuotasIndependent() throws Exception {
        sendGet("/api/event/getAll", null, null);
        sendGet("/api/event/getAll", null, null);
        assertThat(sendGet("/api/event/getAll", null, null).statusCode()).isEqualTo(429);

        HttpResponse<String> showResponse = sendGet("/api/v1/shows/42", null, null);

        assertThat(showResponse.statusCode()).isEqualTo(200);
        assertThat(showResponse.headers().firstValue("X-RateLimit-Remaining"))
                .hasValue("1");
        assertThat(EVENT_CALLS).hasValue(2);
        assertThat(SHOW_CALLS).hasValue(1);
    }

    @Test
    void corsPreflightDoesNotConsumeTokens() throws Exception {
        HttpResponse<String> preflight = sendPreflight("/api/v1/venues/1");
        HttpResponse<String> first = sendGet("/api/v1/venues/1", null, null);
        HttpResponse<String> second = sendGet("/api/v1/venues/1", null, null);
        HttpResponse<String> rejected = sendGet("/api/v1/venues/1", null, null);

        assertThat(preflight.statusCode()).isEqualTo(200);
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(rejected.statusCode()).isEqualTo(429);
        assertThat(VENUE_CALLS).hasValue(2);
    }

    @Test
    void excludesManagementAndProtectsInternalRateLimitEndpoint() throws Exception {
        for (int attempt = 0; attempt < 3; attempt++) {
            assertThat(sendGet("/actuator/health", null, null).statusCode()).isEqualTo(200);
        }

        HttpResponse<String> internalEndpoint = sendGet(
                GatewayRateLimitFilter.FALLBACK_PATH,
                null,
                null
        );
        assertThat(internalEndpoint.statusCode()).isEqualTo(404);
        assertThat(internalEndpoint.headers().firstValue("X-RateLimit-Limit")).isEmpty();
    }

    private HttpResponse<String> sendGet(String path, String correlationId, String origin)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + this.gatewayPort + path))
                .timeout(Duration.ofSeconds(5))
                .GET();
        if (correlationId != null) {
            request.header(CorrelationIdFilter.HEADER_NAME, correlationId);
        }
        if (origin != null) {
            request.header("Origin", origin);
        }
        return HttpClient.newHttpClient().send(
                request.build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private HttpResponse<String> sendPreflight(String path)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + this.gatewayPort + path))
                .timeout(Duration.ofSeconds(5))
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();
        return HttpClient.newHttpClient().send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private static HttpServer startDownstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", GatewayRateLimitingTests::respond);
            server.start();
            return server;
        }
        catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void respond(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.startsWith("/api/event")) {
            EVENT_CALLS.incrementAndGet();
        }
        else if (path.startsWith("/api/v1/venues")) {
            VENUE_CALLS.incrementAndGet();
        }
        else if (path.startsWith("/api/v1/shows")) {
            SHOW_CALLS.incrementAndGet();
        }

        byte[] body = path.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
