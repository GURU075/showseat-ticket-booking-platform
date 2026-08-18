# Gateway Downstream Resilience: Circuit Breakers, Fallbacks, and Retry Safety

## 1. What problem are we solving?

The API Gateway normally forwards an Event request like this:

```text
Client -> API Gateway :8088 -> Event Service :8081
```

If Event Service is stopped, the network call fails. Without a resilience policy, the
client may receive an inconsistent framework error and the Gateway may continue making
calls to a service that is already known to be unavailable.

The Event route is used as the detailed teaching example. The same pattern now gives
every downstream route a predictable failure contract:

```text
Event Service available
Client -> Gateway -> Event Service -> normal response

Event Service unavailable
Client -> Gateway -> Circuit Breaker -> internal fallback -> controlled HTTP 503
```

Each service has a separately named breaker and fallback:

| Service | Circuit-breaker ID | Internal fallback | Public error code |
| --- | --- | --- | --- |
| Event | `eventServiceCircuitBreaker` | `/internal/fallback/event-service` | `EVENT_SERVICE_UNAVAILABLE` |
| Venue | `venueServiceCircuitBreaker` | `/internal/fallback/venue-service` | `VENUE_SERVICE_UNAVAILABLE` |
| Show | `showServiceCircuitBreaker` | `/internal/fallback/show-service` | `SHOW_SERVICE_UNAVAILABLE` |
| Seat Inventory | `seatInventoryServiceCircuitBreaker` | `/internal/fallback/seat-inventory-service` | `SEAT_INVENTORY_SERVICE_UNAVAILABLE` |

The breakers do not share state. For example, an OPEN Venue circuit does not block Show
or Seat Inventory requests.

## 2. Simple real-world analogy

Imagine an elevator that repeatedly fails. A circuit breaker behaves like a security
guard:

1. At first, the guard lets people try the elevator.
2. After enough recent failures, the guard closes access temporarily.
3. People immediately receive a clear "temporarily unavailable" answer instead of
   waiting for another failed attempt.
4. After a waiting period, the guard allows a few test attempts.
5. If the elevator works, normal access resumes. If it still fails, access closes again.

The fallback is the clear answer given by the guard. It is not a replacement Event
Service and it does not invent event data.

## 3. Circuit-breaker states

```text
                    failure threshold reached
       +--------+ ------------------------------> +------+
       | CLOSED |                                 | OPEN |
       +--------+ <------------------------------ +------+
           ^          trial calls succeed             |
           |                                          | wait duration ends
           |                                          v
           |                                    +-----------+
           +------------------------------------| HALF_OPEN |
                 trial calls succeed            +-----------+
                                                       |
                                                       | trial calls fail
                                                       v
                                                    OPEN
```

### CLOSED

Requests are sent to Event Service. The breaker records recent success and failure
results.

### OPEN

Requests are not sent to Event Service. They fail fast and use the fallback. This
prevents repeated network work while the service is known to be unhealthy.

### HALF_OPEN

After the waiting period, a limited number of trial requests are allowed. Successful
trials close the breaker; failed trials open it again.

## 4. Dependency

The Gateway uses this Maven dependency:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
</dependency>
```

This is the non-reactive Resilience4j starter. It matches this Servlet/Web MVC Gateway.
The reactive starter with `reactor` in its artifact name is intended for reactive
applications and is not required here. The Gateway configuration prefix remains:

```text
spring.cloud.gateway.server.webmvc
```

The starter provides:

- Spring Cloud CircuitBreaker abstraction;
- Resilience4j as the circuit-breaker implementation;
- time-limiter support required by the Gateway filter;
- Micrometer integration when Actuator is available.

Do not add a separate version to this dependency. The imported Spring Cloud BOM selects
the version compatible with the project's Spring Cloud release.

## 5. Reference route configuration: Event Service

```yaml
- id: event-service-route
  uri: ${EVENT_SERVICE_URL:http://localhost:8081}
  predicates:
    - Path=/api/event/**
  filters:
    - name: CircuitBreaker
      args:
        id: eventServiceCircuitBreaker
        fallbackUri: forward:/internal/fallback/event-service
        statusCodes:
          - 500
          - 502
          - 503
          - 504
```

### `filters`

Filters add behavior before or after the downstream call. This filter wraps only the
Event Service route.

### `name: CircuitBreaker`

Selects Spring Cloud Gateway's CircuitBreaker route filter.

### `id: eventServiceCircuitBreaker`

Names the circuit-breaker instance. The same exact name must appear under:

```text
resilience4j.circuitbreaker.instances
resilience4j.timelimiter.instances
```

This project uses `id` because Gateway MVC 5.0.2 binds the filter configuration through
the circuit-breaker config object's `id` field. Using `name` in this exact project
causes the runtime error:

```text
A CircuitBreaker must have an id.
```

This is why configuration must be verified with a running integration test, not only by
checking whether the application compiles.

### `fallbackUri`

```yaml
fallbackUri: forward:/internal/fallback/event-service
```

`forward:` performs an internal server-side forward. It does not redirect the client
and it does not make another network call.

The browser/client still sees its original URL:

```text
/api/event/getAll
```

Only `forward:` fallback URIs are supported by this Gateway filter.

### `statusCodes`

Connection errors and timeouts already count as failures. The configured status codes
also make these Event Service responses use the fallback:

| Status | Meaning |
| ---: | --- |
| `500` | Event Service internal error |
| `502` | Event Service received a bad upstream response |
| `503` | Event Service unavailable |
| `504` | Event Service upstream timeout |

Client errors such as `400`, `404`, and `409` are not included. They are legitimate API
responses and should be returned unchanged instead of being treated as infrastructure
failures.

Venue, Show, and Seat Inventory use the same filter structure with their own breaker ID
and fallback URI. Separate IDs are essential because circuit state belongs to one
downstream dependency, not to the entire Gateway.

## 6. Resilience4j configuration

```yaml
resilience4j:
  circuitbreaker:
    instances:
      eventServiceCircuitBreaker:
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 50
        permittedNumberOfCallsInHalfOpenState: 3
        waitDurationInOpenState: 10s
        automaticTransitionFromOpenToHalfOpenEnabled: true
        registerHealthIndicator: false
  timelimiter:
    instances:
      eventServiceCircuitBreaker:
        timeoutDuration: 4s
        cancelRunningFuture: true
```

### `slidingWindowType: COUNT_BASED`

The breaker evaluates the most recent number of calls, rather than calls within a time
period. This makes the learning example easier to reason about.

### `slidingWindowSize: 10`

The breaker remembers the results of the latest 10 calls when calculating its failure
rate.

### `minimumNumberOfCalls: 5`

The breaker waits for at least 5 recorded calls before it can open. A single temporary
failure will therefore not open the circuit immediately.

Important distinction: an individual failed call still uses the fallback. The minimum
call count controls when future calls are blocked by the OPEN state.

### `failureRateThreshold: 50`

After the minimum call count is reached, a failure rate of 50 percent or more opens the
circuit.

Example after five calls:

```text
3 failures / 5 total calls = 60% failure rate -> circuit opens
```

### `permittedNumberOfCallsInHalfOpenState: 3`

When HALF_OPEN, only three trial calls are allowed to decide whether Event Service has
recovered.

### `waitDurationInOpenState: 10s`

The circuit remains OPEN for 10 seconds before moving to HALF_OPEN.

### `automaticTransitionFromOpenToHalfOpenEnabled: true`

The breaker moves to HALF_OPEN after the waiting period without requiring a client call
to trigger the state transition.

### `registerHealthIndicator: false`

An Event Service outage should not mark the API Gateway process itself as unhealthy.
Otherwise, an orchestrator might repeatedly restart a healthy Gateway because one
downstream dependency is unavailable.

Metrics can still be collected through the Actuator/Micrometer integration without
turning the downstream circuit state into the Gateway's health status.

### `timeoutDuration: 4s`

A downstream call is not allowed to occupy the resilience operation indefinitely. The
time limiter activates the fallback after four seconds.

This is deliberately shorter than the Gateway HTTP client's five-second read timeout:

```yaml
spring.http.clients.read-timeout: 5s
```

The resilience layer therefore produces the controlled response before the lower-level
HTTP read timeout is reached.

### `cancelRunningFuture: true`

The framework requests cancellation of timed-out work. Cancellation is best-effort;
the underlying I/O implementation decides how quickly it can stop its work.

## 7. Environment-variable overrides

The defaults are suitable for local learning, and each important setting is externally
configurable:

| Environment variable | Default | Purpose |
| --- | ---: | --- |
| `EVENT_CIRCUIT_BREAKER_WINDOW_SIZE` | `10` | Number of recent calls evaluated |
| `EVENT_CIRCUIT_BREAKER_MINIMUM_CALLS` | `5` | Calls required before opening |
| `EVENT_CIRCUIT_BREAKER_FAILURE_RATE` | `50` | Failure percentage threshold |
| `EVENT_CIRCUIT_BREAKER_HALF_OPEN_CALLS` | `3` | Recovery trial calls |
| `EVENT_CIRCUIT_BREAKER_OPEN_WAIT` | `10s` | OPEN-state waiting time |
| `EVENT_SERVICE_TIMEOUT` | `4s` | Per-call resilience timeout |

The other services expose equivalent variables with these prefixes:

| Service | Circuit-breaker prefix | Timeout variable |
| --- | --- | --- |
| Venue | `VENUE_CIRCUIT_BREAKER_...` | `VENUE_SERVICE_TIMEOUT` |
| Show | `SHOW_CIRCUIT_BREAKER_...` | `SHOW_SERVICE_TIMEOUT` |
| Seat Inventory | `SEAT_INVENTORY_CIRCUIT_BREAKER_...` | `SEAT_INVENTORY_SERVICE_TIMEOUT` |

For example, `VENUE_CIRCUIT_BREAKER_OPEN_WAIT=20s` changes only the Venue circuit's
OPEN-state wait and its fallback `Retry-After` value.

Production values should be chosen from real latency and failure data. There is no
single correct configuration for every service.

## 8. Controlled fallback response

When Event Service is unavailable, the Gateway returns:

```http
HTTP/1.1 503 Service Unavailable
Content-Type: application/problem+json
Cache-Control: no-store
Retry-After: 10
X-Correlation-ID: event-request-123
```

Example body:

```json
{
  "type": "urn:showseat:problem:event-service-unavailable",
  "title": "Event Service Unavailable",
  "status": 503,
  "detail": "Event Service is temporarily unavailable. Please try again later.",
  "code": "EVENT_SERVICE_UNAVAILABLE",
  "path": "/api/event/getAll",
  "correlationId": "event-request-123"
}
```

Why `503`?

The Gateway is running, but the service required to complete this request is temporarily
unavailable. This is different from `500`, which would suggest an unexpected error in
the Gateway itself.

Why `application/problem+json`?

Spring's `ProblemDetail` provides a consistent, machine-readable error structure based
on the HTTP Problem Details standard.

Why `Retry-After: 10`?

It gives a cooperative client a hint about when retrying may make sense. It is a hint,
not a guarantee that Event Service will recover in exactly 10 seconds. Its value is
derived from `EVENT_CIRCUIT_BREAKER_OPEN_WAIT`, so changing the OPEN-state wait keeps the
response hint consistent.

Why `Cache-Control: no-store`?

An intermediary should not cache a temporary outage response and continue returning it
after Event Service has recovered.

Why include `correlationId`?

The client can report this ID to support, and the same value can be searched in Gateway
logs. Internal exception class names and stack traces are deliberately not returned.

## 9. Protecting the internal fallback

The fallback path is an implementation detail:

```text
/internal/fallback/event-service
```

The controller accepts it only during a server-side `FORWARD`. A client calling that
path directly receives `404 Not Found`.

This prevents clients from treating an internal handler as a public API endpoint.

## 10. Circuit breaker is not retry

These patterns solve different problems:

| Pattern | Main purpose |
| --- | --- |
| Circuit breaker | Stop calling a dependency that is repeatedly failing |
| Timeout | Limit how long one call may wait |
| Retry | Make another attempt after a failed attempt |
| Fallback | Return a controlled alternative response |

Every downstream route has a circuit breaker, timeout, and fallback. None has a Gateway
`Retry` filter.

Although a transitive library may place retry classes on the classpath, no retry occurs
unless retry behavior is explicitly configured and invoked.

## 11. Why POST is not automatically retried

A POST request commonly creates or changes data:

```http
POST /api/event
```

Imagine that Event Service creates the event, but its response is lost because the
network connection breaks. If the Gateway automatically sends the POST again, Event
Service might create a duplicate event.

```text
First POST  -> event created, response lost
Second POST -> duplicate event created
```

Therefore this implementation makes only one downstream attempt for POST requests.
Automatic retries should be considered only when all of these are true:

1. the operation is known to be idempotent, or an idempotency-key contract exists;
2. the retryable failure types are narrowly defined;
3. attempts are bounded;
4. exponential backoff and jitter are used;
5. tests prove that duplicate side effects cannot occur.

Do not assume every GET is automatically safe either. API semantics and downstream
implementation must still be reviewed.

## 12. Request flows

### Healthy Event Service

```text
1. Client sends GET /api/event/getAll.
2. CorrelationIdFilter assigns or preserves X-Correlation-ID.
3. The Event route predicate matches.
4. CircuitBreaker permits the call.
5. Gateway forwards the unchanged path to Event Service.
6. Event Service returns 200.
7. CircuitBreaker records success.
8. Gateway returns the Event Service response.
```

### Event Service stopped while circuit is CLOSED

```text
1. Client sends GET /api/event/getAll.
2. CircuitBreaker permits the initial call.
3. The connection to Event Service fails.
4. CircuitBreaker records failure.
5. Gateway internally forwards to the fallback controller.
6. Fallback returns the controlled 503 response.
```

### Circuit is OPEN

```text
1. Client sends GET /api/event/getAll.
2. CircuitBreaker rejects the downstream call immediately.
3. No call is made to Event Service.
4. Gateway internally forwards to the fallback controller.
5. Client receives the controlled 503 response quickly.
```

## 13. Automated tests

The project verifies these behaviors:

1. normal Event requests still route successfully;
2. Event Service stopped on an unavailable port returns controlled `503`;
3. the response contains the correlation ID and safe problem details;
4. exception class names are not leaked;
5. `Retry-After` and `Cache-Control` headers are present;
6. a failing POST reaches the downstream stub exactly once;
7. the internal fallback cannot be called directly;
8. for each of the four services, after five configured failures a sixth call is
   rejected without reaching that downstream stub, proving that its circuit is OPEN;
9. all existing Venue, Show, Inventory, health, and correlation-ID tests still pass.

The POST invocation-count assertion is important. Checking only the final status would
not prove whether the Gateway made one attempt or several attempts.

## 14. Manual testing

### Healthy-service test

Start Event Service and Gateway, then run:

```powershell
curl.exe -i `
  -H "X-Correlation-ID: healthy-event-test" `
  http://localhost:8088/api/event/getAll
```

Expected result: the normal Event Service response.

### Stopped-service test

Stop Event Service but keep the Gateway running:

```powershell
curl.exe -i `
  -H "X-Correlation-ID: stopped-event-test" `
  http://localhost:8088/api/event/getAll
```

Expected result:

```text
HTTP 503
code = EVENT_SERVICE_UNAVAILABLE
correlationId = stopped-event-test
```

The Gateway log should include:

```text
[correlationId=stopped-event-test] Event Service fallback activated ...
```

## 15. Common mistakes

### Sharing one breaker across every route

Use a separately named circuit breaker and separately tunable policy for each downstream
service. One shared breaker can incorrectly block healthy services because another
service failed. This project therefore has four independent breaker IDs.

### Returning HTTP 200 from fallback

A friendly JSON body with status 200 hides the outage from clients, monitoring, and
load balancers. The fallback should preserve honest HTTP semantics.

### Returning the exception message or stack trace

Internal hostnames, class names, and infrastructure details can leak. Log diagnostic
details internally and return a stable public error contract.

### Retrying every method

Retrying POST can duplicate side effects. A circuit breaker does not require a retry
policy.

### Treating 4xx responses as circuit-breaker failures

Most 4xx responses describe a client or business problem, not an unavailable service.
Opening the circuit for ordinary 404 or validation responses would be incorrect.

### Using the same timeout at every layer

Layered timeouts should be intentional. Here, the four-second resilience timeout is
shorter than the five-second HTTP read timeout.

### Exposing the fallback as a public API

The internal handler should not become a documented endpoint that clients call
directly.

## 16. Interview questions and short answers

### What is a circuit breaker?

A circuit breaker monitors calls to a dependency and temporarily blocks new calls when
recent failures exceed a configured threshold. It fails fast while the dependency is
likely unavailable and later permits trial calls to detect recovery.

### What are CLOSED, OPEN, and HALF_OPEN?

- CLOSED sends calls and records their results.
- OPEN rejects calls immediately.
- HALF_OPEN permits a limited number of recovery tests.

### Is a circuit breaker the same as retry?

No. A circuit breaker prevents calls during repeated failure. Retry creates additional
attempts after failure. They have different goals and risks.

### Why return 503 from this fallback?

The Gateway is reachable, but Event Service cannot currently serve the operation.
`503 Service Unavailable` communicates that temporary dependency failure accurately.

### Why should POST not be retried automatically?

The first attempt may have committed its side effect even if its response was lost.
Repeating it can create duplicate data unless an idempotency contract exists.

### Why not mark Gateway health DOWN when the Event circuit opens?

The Gateway process may be completely healthy. Restarting it will not repair Event
Service and can turn a partial outage into a wider outage.

### Where should the circuit breaker live?

It should protect the component making the remote call. Here, the Gateway owns the
client-facing downstream routes, so each route filter protects its call. Internal
service-to-service clients may also need their own breakers independently.

### What is a fallback?

A fallback is controlled behavior used when the protected call cannot complete. Here it
returns an honest 503 problem response; it does not fabricate successful business data.

### How do you choose circuit-breaker values?

Start with service latency objectives and observed failure patterns, then validate with
metrics and failure testing. Thresholds should not be copied blindly between services.

## 17. Production checklist

- [ ] Each downstream service has its own circuit-breaker name and policy.
- [ ] Timeouts are based on latency objectives and measured behavior.
- [ ] Fallbacks return accurate non-2xx status codes.
- [ ] Error responses never expose exception or infrastructure details.
- [ ] Correlation IDs appear in fallback responses and logs.
- [ ] POST/PUT/PATCH retries require an explicit idempotency decision.
- [ ] Retry attempts, if any, are bounded and use backoff plus jitter.
- [ ] Circuit states, rejected calls, failures, and latency are monitored.
- [ ] Alerts distinguish Gateway failure from downstream failure.
- [ ] Outage behavior is covered by automated integration tests.
- [ ] Recovery from OPEN through HALF_OPEN to CLOSED is exercised before production.

## 18. One-minute summary

Event, Venue, Show, and Seat Inventory routes each have an independent Resilience4j
circuit breaker. Connection failures, timeouts, and selected server-side 5xx responses
use service-specific internal fallbacks that return stable
`503 application/problem+json` responses. Repeated failures open only the affected service's
circuit, so later calls fail fast without blocking healthy services. The configuration
does not retry requests, and an automated test proves a failing POST reaches Event
Service only once.

## 19. Further reading

- [Spring Cloud Gateway MVC CircuitBreaker filter](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webmvc/filters/circuitbreaker-filter.html)
- [Spring Cloud CircuitBreaker Resilience4j configuration](https://docs.spring.io/spring-cloud-circuitbreaker/reference/spring-cloud-circuitbreaker-resilience4j/circuit-breaker-properties-configuration.html)
- [RFC 9457: Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457.html)
