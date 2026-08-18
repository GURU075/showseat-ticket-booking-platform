package com.guru.api_gateway;

import com.guru.api_gateway.filter.CorrelationIdFilter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DownstreamServiceResilienceTests {

    private static final int STOPPED_SERVICE_PORT = findUnusedPort();

    @LocalServerPort
    private int gatewayPort;

    @DynamicPropertySource
    static void stoppedDownstreamServices(DynamicPropertyRegistry registry) {
        String stoppedServiceUrl = "http://127.0.0.1:" + STOPPED_SERVICE_PORT;
        registry.add("EVENT_SERVICE_URL", () -> stoppedServiceUrl);
        registry.add("VENUE_SERVICE_URL", () -> stoppedServiceUrl);
        registry.add("SHOW_SERVICE_URL", () -> stoppedServiceUrl);
        registry.add("SEAT_INVENTORY_SERVICE_URL", () -> stoppedServiceUrl);
    }

    @ParameterizedTest(name = "{0} returns its controlled 503 response")
    @MethodSource("stoppedServiceCases")
    void returnsControlledServiceUnavailableResponseWhenDownstreamServiceIsStopped(
            String serviceName,
            String requestPath,
            String expectedErrorCode
    ) throws Exception {
        String correlationId = serviceName.toLowerCase().replace(' ', '-') + "-outage-test";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + this.gatewayPort + requestPath))
                .timeout(Duration.ofSeconds(5))
                .header(CorrelationIdFilter.HEADER_NAME, correlationId)
                .header("Origin", "http://localhost:5173")
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.headers().firstValue(CorrelationIdFilter.HEADER_NAME))
                .hasValue(correlationId);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
                .hasValue("http://localhost:5173");
        assertThat(response.headers().firstValue("Access-Control-Expose-Headers").orElse(""))
                .containsIgnoringCase("Retry-After")
                .containsIgnoringCase("X-Correlation-ID");
        assertThat(response.headers().firstValue("Retry-After")).hasValue("10");
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .startsWith("application/problem+json");
        assertThat(response.body())
                .contains("\"status\":503")
                .contains("\"code\":\"" + expectedErrorCode + "\"")
                .contains("\"path\":\"" + requestPath + "\"")
                .contains("\"correlationId\":\"" + correlationId + "\"")
                .doesNotContain("ClosedChannelException")
                .doesNotContain("ResourceAccessException");
    }

    private static Stream<Arguments> stoppedServiceCases() {
        return Stream.of(
                Arguments.of(
                        "Event Service",
                        "/api/event/getAll",
                        "EVENT_SERVICE_UNAVAILABLE"
                ),
                Arguments.of(
                        "Venue Service",
                        "/api/v1/venues/1",
                        "VENUE_SERVICE_UNAVAILABLE"
                ),
                Arguments.of(
                        "Show Service",
                        "/api/v1/shows/42",
                        "SHOW_SERVICE_UNAVAILABLE"
                ),
                Arguments.of(
                        "Seat Inventory Service",
                        "/api/v1/show-seats/shows/42",
                        "SEAT_INVENTORY_SERVICE_UNAVAILABLE"
                )
        );
    }

    private static int findUnusedPort() {
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
        catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
