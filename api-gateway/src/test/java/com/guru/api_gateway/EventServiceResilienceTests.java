package com.guru.api_gateway;

import com.guru.api_gateway.filter.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EventServiceResilienceTests {

    private static final int STOPPED_EVENT_SERVICE_PORT = findUnusedPort();

    @LocalServerPort
    private int gatewayPort;

    @DynamicPropertySource
    static void stoppedEventService(DynamicPropertyRegistry registry) {
        registry.add(
                "EVENT_SERVICE_URL",
                () -> "http://127.0.0.1:" + STOPPED_EVENT_SERVICE_PORT
        );
    }

    @Test
    void returnsControlledServiceUnavailableResponseWhenEventServiceIsStopped()
            throws Exception {
        String correlationId = "event-service-outage-test";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "http://127.0.0.1:" + this.gatewayPort + "/api/event/getAll"
                ))
                .timeout(Duration.ofSeconds(5))
                .header(CorrelationIdFilter.HEADER_NAME, correlationId)
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.headers().firstValue(CorrelationIdFilter.HEADER_NAME))
                .hasValue(correlationId);
        assertThat(response.headers().firstValue("Retry-After")).hasValue("10");
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .startsWith("application/problem+json");
        assertThat(response.body())
                .contains("\"status\":503")
                .contains("\"code\":\"EVENT_SERVICE_UNAVAILABLE\"")
                .contains("\"path\":\"/api/event/getAll\"")
                .contains("\"correlationId\":\"" + correlationId + "\"")
                .doesNotContain("ClosedChannelException")
                .doesNotContain("ResourceAccessException");
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
