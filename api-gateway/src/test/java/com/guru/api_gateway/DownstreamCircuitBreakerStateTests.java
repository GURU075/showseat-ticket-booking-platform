package com.guru.api_gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static com.guru.api_gateway.support.DiscoveryTestSupport.disableEureka;
import static com.guru.api_gateway.support.DiscoveryTestSupport.registerInstance;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DownstreamCircuitBreakerStateTests {

    private static final ConcurrentMap<String, AtomicInteger> DOWNSTREAM_ATTEMPTS =
            new ConcurrentHashMap<>();
    private static final HttpServer FAILING_DOWNSTREAM_SERVICE = startFailingService();

    @LocalServerPort
    private int gatewayPort;

    @DynamicPropertySource
    static void failingDownstreamServices(DynamicPropertyRegistry registry) {
        String failingServiceUrl = "http://127.0.0.1:"
                + FAILING_DOWNSTREAM_SERVICE.getAddress().getPort();
        disableEureka(registry);
        registerInstance(registry, "event-service", 0, failingServiceUrl);
        registerInstance(registry, "venue-service", 0, failingServiceUrl);
        registerInstance(registry, "show-service", 0, failingServiceUrl);
        registerInstance(registry, "seat-inventory-service", 0, failingServiceUrl);
        registerInstance(registry, "booking-service", 0, failingServiceUrl);
    }

    @AfterAll
    static void stopFailingService() {
        FAILING_DOWNSTREAM_SERVICE.stop(0);
    }

    @ParameterizedTest(name = "{0} circuit opens and fails fast")
    @MethodSource("circuitBreakerCases")
    void opensEachCircuitAfterConfiguredFailureThresholdAndThenFailsFast(
            String serviceName,
            String requestPath,
            String expectedErrorCode
    ) throws Exception {
        for (int requestNumber = 1; requestNumber <= 5; requestNumber++) {
            assertThat(sendFailingRequest(requestPath).statusCode()).isEqualTo(503);
        }

        assertThat(attemptsFor(requestPath)).hasValue(5);

        HttpResponse<String> shortCircuitedResponse = sendFailingRequest(requestPath);

        assertThat(shortCircuitedResponse.statusCode()).isEqualTo(503);
        assertThat(shortCircuitedResponse.body())
                .contains("\"code\":\"" + expectedErrorCode + "\"");
        assertThat(attemptsFor(requestPath))
                .as("OPEN %s circuit must skip its downstream service", serviceName)
                .hasValue(5);
    }

    private HttpResponse<String> sendFailingRequest(String requestPath)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + this.gatewayPort + requestPath))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        return HttpClient.newHttpClient().send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private static AtomicInteger attemptsFor(String requestPath) {
        return DOWNSTREAM_ATTEMPTS.computeIfAbsent(requestPath, ignored -> new AtomicInteger());
    }

    private static Stream<Arguments> circuitBreakerCases() {
        return Stream.of(
                Arguments.of(
                        "Event Service",
                        "/api/event/always-fails",
                        "EVENT_SERVICE_UNAVAILABLE"
                ),
                Arguments.of(
                        "Venue Service",
                        "/api/v1/venues/always-fails",
                        "VENUE_SERVICE_UNAVAILABLE"
                ),
                Arguments.of(
                        "Show Service",
                        "/api/v1/shows/always-fails",
                        "SHOW_SERVICE_UNAVAILABLE"
                ),
                Arguments.of(
                        "Seat Inventory Service",
                        "/api/v1/show-seats/always-fails",
                        "SEAT_INVENTORY_SERVICE_UNAVAILABLE"
                ),
                Arguments.of(
                        "Booking Service",
                        "/api/v1/bookings/always-fails",
                        "BOOKING_SERVICE_UNAVAILABLE"
                )
        );
    }

    private static HttpServer startFailingService() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                attemptsFor(exchange.getRequestURI().getPath()).incrementAndGet();
                exchange.sendResponseHeaders(503, -1);
                exchange.close();
            });
            server.start();
            return server;
        }
        catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
