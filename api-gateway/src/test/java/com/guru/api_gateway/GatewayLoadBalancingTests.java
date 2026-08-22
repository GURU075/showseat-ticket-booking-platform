package com.guru.api_gateway;

import com.sun.net.httpserver.HttpExchange;
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
import java.util.HashSet;
import java.util.Set;

import static com.guru.api_gateway.support.DiscoveryTestSupport.disableEureka;
import static com.guru.api_gateway.support.DiscoveryTestSupport.registerInstance;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayLoadBalancingTests {

    private static final HttpServer EVENT_INSTANCE_ONE = startEventInstance("event-1");
    private static final HttpServer EVENT_INSTANCE_TWO = startEventInstance("event-2");

    @LocalServerPort
    private int gatewayPort;

    @DynamicPropertySource
    static void discoveredInstances(DynamicPropertyRegistry registry) {
        disableEureka(registry);
        registerInstance(registry, "event-service", 0, EVENT_INSTANCE_ONE);
        registerInstance(registry, "event-service", 1, EVENT_INSTANCE_TWO);
    }

    @AfterAll
    static void stopInstances() {
        EVENT_INSTANCE_ONE.stop(0);
        EVENT_INSTANCE_TWO.stop(0);
    }

    @Test
    void distributesRequestsAcrossDiscoveredEventServiceInstances() throws Exception {
        Set<String> selectedInstances = new HashSet<>();

        for (int requestNumber = 0; requestNumber < 6; requestNumber++) {
            HttpResponse<String> response = sendGet("/api/event/getAll");
            assertThat(response.statusCode()).isEqualTo(200);
            selectedInstances.add(
                    response.headers().firstValue("X-Service-Instance").orElseThrow()
            );
        }

        assertThat(selectedInstances).containsExactlyInAnyOrder("event-1", "event-2");
    }

    @Test
    void returnsControlledFallbackWhenNoServiceInstanceIsRegistered() throws Exception {
        HttpResponse<String> response = sendGet("/api/v1/shows/42");

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.headers().firstValue("Retry-After")).hasValue("10");
        assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .startsWith("application/problem+json");
        assertThat(response.body())
                .contains("\"code\":\"SHOW_SERVICE_UNAVAILABLE\"")
                .doesNotContain("No servers available");
    }

    private HttpResponse<String> sendGet(String path)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + this.gatewayPort + path))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        return HttpClient.newHttpClient().send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private static HttpServer startEventInstance(String instanceId) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> respond(exchange, instanceId));
            server.start();
            return server;
        }
        catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void respond(HttpExchange exchange, String instanceId) throws IOException {
        exchange.getResponseHeaders().set("X-Service-Instance", instanceId);
        exchange.sendResponseHeaders(200, -1);
        exchange.close();
    }
}
