package com.guru.api_gateway;

import com.guru.api_gateway.filter.CorrelationIdFilter;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewayApplicationTests {

	private static final HttpServer EVENT_SERVICE = startService("event");
	private static final HttpServer VENUE_SERVICE = startService("venue");
	private static final HttpServer SHOW_SERVICE = startService("show");
	private static final HttpServer SEAT_INVENTORY_SERVICE = startService("inventory");
	private static final AtomicInteger FAILED_EVENT_POST_ATTEMPTS = new AtomicInteger();

	@LocalServerPort
	private int gatewayPort;

	@DynamicPropertySource
	static void downstreamServiceUrls(DynamicPropertyRegistry registry) {
		registerServiceUrl(registry, "EVENT_SERVICE_URL", EVENT_SERVICE);
		registerServiceUrl(registry, "VENUE_SERVICE_URL", VENUE_SERVICE);
		registerServiceUrl(registry, "SHOW_SERVICE_URL", SHOW_SERVICE);
		registerServiceUrl(registry, "SEAT_INVENTORY_SERVICE_URL", SEAT_INVENTORY_SERVICE);
	}

	@AfterAll
	static void stopDownstreamServices() {
		List.of(EVENT_SERVICE, VENUE_SERVICE, SHOW_SERVICE, SEAT_INVENTORY_SERVICE)
				.forEach(server -> server.stop(0));
	}

	@Test
	void routesEventRequestsWithoutChangingThePath() throws Exception {
		assertProxiedTo("/api/event/getAll", "event:/api/event/getAll");
	}

	@Test
	void routesEveryVenueServicePathToTheSameService() throws Exception {
		assertProxiedTo("/api/v1/cities", "venue:/api/v1/cities");
		assertProxiedTo("/api/v1/venues/1", "venue:/api/v1/venues/1");
		assertProxiedTo("/api/v1/screens/10", "venue:/api/v1/screens/10");
		assertProxiedTo(
				"/api/v1/venue-seats?screenId=10",
				"venue:/api/v1/venue-seats?screenId=10"
		);
	}

	@Test
	void routesShowRequestsWithoutChangingThePath() throws Exception {
		assertProxiedTo("/api/v1/shows/42", "show:/api/v1/shows/42");
	}

	@Test
	void routesShowSeatInventoryWithoutCollidingWithVenueSeats() throws Exception {
		assertProxiedTo(
				"/api/v1/show-seats/shows/42",
				"inventory:/api/v1/show-seats/shows/42"
		);
	}

	@Test
	void doesNotRouteTheRemovedAmbiguousSeatPath() throws Exception {
		HttpResponse<String> response = sendGet("/api/v1/seats");

		assertThat(response.statusCode()).isEqualTo(404);
	}

	@Test
	void exposesOnlyTheConfiguredHealthEndpoint() throws Exception {
		HttpResponse<String> health = sendGet("/actuator/health");
		HttpResponse<String> environment = sendGet("/actuator/env");

		assertThat(health.statusCode()).isEqualTo(200);
		assertThat(health.body()).contains("\"status\":\"UP\"");
		assertThat(environment.statusCode()).isEqualTo(404);
	}

	@Test
	void preservesAValidClientCorrelationIdAcrossTheGateway() throws Exception {
		String correlationId = "checkout-request-123";

		HttpResponse<String> response = sendGet("/api/event/getAll", correlationId);

		assertThat(response.headers().firstValue(CorrelationIdFilter.HEADER_NAME))
				.hasValue(correlationId);
		assertThat(response.headers().firstValue("X-Downstream-Correlation-ID"))
				.hasValue(correlationId);
	}

	@Test
	void generatesAndForwardsACorrelationIdWhenTheHeaderIsMissing() throws Exception {
		HttpResponse<String> response = sendGet("/api/event/getAll");

		String generatedId = response.headers()
				.firstValue(CorrelationIdFilter.HEADER_NAME)
				.orElseThrow();
		assertThatCode(() -> UUID.fromString(generatedId)).doesNotThrowAnyException();
		assertThat(response.headers().firstValue("X-Downstream-Correlation-ID"))
				.hasValue(generatedId);
	}

	@Test
	void replacesAnUnsafeClientCorrelationId() throws Exception {
		String unsafeCorrelationId = "unsafe id with spaces";

		HttpResponse<String> response = sendGet("/api/event/getAll", unsafeCorrelationId);

		String generatedId = response.headers()
				.firstValue(CorrelationIdFilter.HEADER_NAME)
				.orElseThrow();
		assertThat(generatedId).isNotEqualTo(unsafeCorrelationId);
		assertThatCode(() -> UUID.fromString(generatedId)).doesNotThrowAnyException();
		assertThat(response.headers().firstValue("X-Downstream-Correlation-ID"))
				.hasValue(generatedId);
	}

	@Test
	void doesNotRetryPostRequestsWhenEventServiceFails() throws Exception {
		int attemptsBeforeRequest = FAILED_EVENT_POST_ATTEMPTS.get();

		HttpResponse<String> response = sendPost(
				"/api/event/test-downstream-failure",
				"{\"name\":\"test event\"}"
		);

		assertThat(response.statusCode()).isEqualTo(503);
		assertThat(response.body()).contains("\"code\":\"EVENT_SERVICE_UNAVAILABLE\"");
		assertThat(FAILED_EVENT_POST_ATTEMPTS.get() - attemptsBeforeRequest).isEqualTo(1);
	}

	@Test
	void doesNotExposeInternalFallbackEndpointsDirectly() throws Exception {
		for (String path : List.of(
				"/internal/fallback/event-service",
				"/internal/fallback/venue-service",
				"/internal/fallback/show-service",
				"/internal/fallback/seat-inventory-service"
		)) {
			HttpResponse<String> response = sendGet(path);
			assertThat(response.statusCode())
					.as("internal fallback %s must not be public", path)
					.isEqualTo(404);
		}
	}

	private void assertProxiedTo(String path, String expectedBody) throws Exception {
		HttpResponse<String> response = sendGet(path);

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).isEqualTo(expectedBody);
	}

	private HttpResponse<String> sendGet(String path) throws IOException, InterruptedException {
		return sendGet(path, null);
	}

	private HttpResponse<String> sendGet(String path, String correlationId)
			throws IOException, InterruptedException {
		HttpClient client = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(2))
				.build();
		HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + this.gatewayPort + path))
				.timeout(Duration.ofSeconds(5))
				.GET();
		if (correlationId != null) {
			requestBuilder.header(CorrelationIdFilter.HEADER_NAME, correlationId);
		}
		HttpRequest request = requestBuilder.build();
		return client.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> sendPost(String path, String body)
			throws IOException, InterruptedException {
		HttpClient client = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(2))
				.build();
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + this.gatewayPort + path))
				.timeout(Duration.ofSeconds(5))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();
		return client.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private static void registerServiceUrl(
			DynamicPropertyRegistry registry,
			String property,
			HttpServer server
	) {
		registry.add(property, () -> "http://127.0.0.1:" + server.getAddress().getPort());
	}

	private static HttpServer startService(String serviceName) {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/", exchange -> respondWithRequestTarget(exchange, serviceName));
			server.start();
			return server;
		}
		catch (IOException exception) {
			throw new ExceptionInInitializerError(exception);
		}
	}

	private static void respondWithRequestTarget(HttpExchange exchange, String serviceName)
			throws IOException {
		if (serviceName.equals("event")
				&& exchange.getRequestMethod().equals("POST")
				&& exchange.getRequestURI().getPath().equals("/api/event/test-downstream-failure")) {
			FAILED_EVENT_POST_ATTEMPTS.incrementAndGet();
			exchange.sendResponseHeaders(503, -1);
			exchange.close();
			return;
		}

		String body = serviceName + ":" + exchange.getRequestURI();
		byte[] response = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "text/plain");
		String correlationId = exchange.getRequestHeaders()
				.getFirst(CorrelationIdFilter.HEADER_NAME);
		if (correlationId != null) {
			exchange.getResponseHeaders().set("X-Downstream-Correlation-ID", correlationId);
		}
		exchange.sendResponseHeaders(200, response.length);
		exchange.getResponseBody().write(response);
		exchange.close();
	}
}
