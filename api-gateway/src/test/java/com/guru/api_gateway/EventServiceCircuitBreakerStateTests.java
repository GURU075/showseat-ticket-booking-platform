package com.guru.api_gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EventServiceCircuitBreakerStateTests {

    private static final AtomicInteger DOWNSTREAM_ATTEMPTS = new AtomicInteger();
    private static final HttpServer FAILING_EVENT_SERVICE = startFailingEventService();

    @LocalServerPort
    private int gatewayPort;

    @DynamicPropertySource
    static void failingEventService(DynamicPropertyRegistry registry) {
        registry.add(
                "EVENT_SERVICE_URL",
                () -> "http://127.0.0.1:" + FAILING_EVENT_SERVICE.getAddress().getPort()
        );
    }

    @AfterAll
    static void stopFailingEventService() {
        FAILING_EVENT_SERVICE.stop(0);
    }

    @Test
    void opensCircuitAfterConfiguredFailureThresholdAndThenFailsFast() throws Exception {
        for (int requestNumber = 1; requestNumber <= 5; requestNumber++) {
            assertThat(sendFailingRequest().statusCode()).isEqualTo(503);
        }

        assertThat(DOWNSTREAM_ATTEMPTS).hasValue(5);

        HttpResponse<String> shortCircuitedResponse = sendFailingRequest();

        assertThat(shortCircuitedResponse.statusCode()).isEqualTo(503);
        assertThat(shortCircuitedResponse.body())
                .contains("\"code\":\"EVENT_SERVICE_UNAVAILABLE\"");
        assertThat(DOWNSTREAM_ATTEMPTS).as("OPEN circuit must skip Event Service")
                .hasValue(5);
    }

    private HttpResponse<String> sendFailingRequest() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "http://127.0.0.1:" + this.gatewayPort + "/api/event/always-fails"
                ))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        return HttpClient.newHttpClient().send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private static HttpServer startFailingEventService() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                DOWNSTREAM_ATTEMPTS.incrementAndGet();
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
