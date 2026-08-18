# 8. Correlation-ID Filter

## What problem does a correlation ID solve?

One user action can pass through several applications:

```text
Client -> API Gateway -> Show Service -> Venue Service
```

Without a shared identifier, logs from those applications are difficult to connect.
A correlation ID gives every log entry for the same request a common searchable value.

Example:

```text
[correlationId=checkout-request-123] Gateway received GET /api/v1/shows/42
[correlationId=checkout-request-123] Show Service loaded show 42
[correlationId=checkout-request-123] Venue Service loaded screen 10
```

## Project contract

This project uses the HTTP header:

```text
X-Correlation-ID
```

The Gateway applies these rules:

| Incoming value | Gateway behavior |
| --- | --- |
| Valid ID | Preserve it |
| Missing header | Generate a UUID |
| Empty header | Generate a UUID |
| Unsafe value | Generate a UUID |
| Multiple values | Select one canonical value |

A valid client-provided value:

- contains between 1 and 128 characters;
- starts with a letter or digit;
- contains only letters, digits, `.`, `_`, or `-` afterward.

Examples:

```text
Valid:   checkout-123
Valid:   mobile.app_request_456
Valid:   550e8400-e29b-41d4-a716-446655440000
Invalid: request with spaces
Invalid: a value containing a newline
Invalid: a value longer than 128 characters
```

Validation prevents an untrusted header from injecting control characters into logs
or creating excessively large log fields.

## Request flow

```text
Client request
      |
      | X-Correlation-ID may be present or missing
      v
CorrelationIdFilter
      |
      | preserve a safe ID or generate a UUID
      | place ID in MDC
      | wrap the request with one canonical header
      | add the header to the response
      v
Spring Cloud Gateway route
      |
      | forwards X-Correlation-ID
      v
Downstream service
      |
      v
Client receives the same X-Correlation-ID
```

## Why implement it at the Gateway?

The Gateway is the common entry point, so it can guarantee that every routed request
has an ID before entering the internal system. Individual services do not have to
invent unrelated IDs for the same request.

Internal services should still have their own small filter or tracing integration to:

1. read the forwarded header;
2. put it into their own MDC;
3. include it in their logs;
4. propagate it if they call another service.

Gateway propagation alone does not automatically place the value into a downstream
service's MDC.

## Why `OncePerRequestFilter`?

This Gateway uses Spring Cloud Gateway Server Web MVC, which runs on the Servlet stack.
`OncePerRequestFilter` is a natural global boundary for this implementation because it:

- runs before Gateway route handling;
- can wrap the incoming `HttpServletRequest`;
- can set the outgoing client response header;
- works for routed and local endpoints;
- provides a `finally` block for reliable MDC cleanup.

The class is registered with:

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
}
```

`@Component` makes it a Spring bean. `@Order` makes it run early so later Gateway work
can see the correlation ID.

## Important constants

```java
public static final String HEADER_NAME = "X-Correlation-ID";
public static final String MDC_KEY = "correlationId";
```

`HEADER_NAME` is the HTTP contract. `MDC_KEY` is the name used by the logging system.
Keeping them as constants prevents spelling differences across code and tests.

## Resolving the ID

Conceptually:

```java
if (incomingHeaderIsSafe) {
    useIncomingHeader();
}
else {
    generateUuid();
}
```

The Gateway generates UUIDs such as:

```text
550e8400-e29b-41d4-a716-446655440000
```

UUIDs are suitable here because they can be generated locally with a very low chance
of collision and do not require a database call.

## Why the request must be wrapped

Servlet request headers are effectively immutable. Calling code cannot simply do:

```java
request.setHeader(...); // this method does not exist
```

The filter creates an `HttpServletRequestWrapper` and overrides:

```text
getHeader
getHeaders
getHeaderNames
```

This makes the generated or validated correlation ID visible to Spring Cloud Gateway's
downstream HTTP client as if it were an ordinary incoming header.

Overriding all three methods matters. Different frameworks may enumerate header names,
request all values, or request only the first value.

## Returning the ID to the client

The filter sets:

```java
response.setHeader(HEADER_NAME, correlationId);
```

This helps a frontend or API consumer report the exact request ID when an error occurs:

```text
"My request failed. Correlation ID: checkout-request-123"
```

An operator can search logs using that value.

## MDC and logging

MDC means Mapped Diagnostic Context. It is a small key-value context associated with
the thread handling the current request.

The filter stores:

```java
MDC.put("correlationId", correlationId);
```

The YAML logging pattern reads it:

```yaml
logging:
  pattern:
    correlation: "[correlationId=%X{correlationId:-none}] "
```

`%X{correlationId}` reads the MDC value. `none` is displayed when no correlation ID is
available, such as during application startup before an HTTP request exists.

Example request log:

```text
[correlationId=checkout-request-123] Completed gateway request method=GET path=/api/v1/shows/42 status=200 durationMs=18
```

## Why MDC cleanup is mandatory

Servlet servers reuse threads:

```text
Thread 7 handles request A
Thread 7 later handles request B
```

If request A leaves its value in MDC, request B could incorrectly log request A's ID.
That produces misleading operational evidence and can mix information across users.

The filter therefore restores the previous value in a `finally` block:

```java
try {
    filterChain.doFilter(request, response);
}
finally {
    restoreMdc(previousValue);
}
```

`finally` executes for successful responses and exceptions.

## Correlation ID versus other IDs

### Correlation ID versus trace ID

A correlation ID is a simple application-level identifier propagated in a header.
A trace ID belongs to a distributed-tracing system such as OpenTelemetry and connects
spans containing timing and dependency information.

```text
Correlation ID -> searchable relationship between logs
Trace ID       -> full distributed trace with spans and timing
```

For a larger production system, prefer standard distributed tracing. A custom
correlation ID is still a useful learning step and can coexist with tracing when the
contracts are clearly defined.

### Correlation ID versus idempotency key

They solve different problems:

```text
Correlation ID -> observability and debugging
Idempotency key -> prevents duplicate business operations
```

Never use the correlation ID as proof that a payment or booking request was already
processed.

### Correlation ID versus user ID

A correlation ID identifies a request flow, not a person. It must not be used for
authentication or authorization.

## Tests implemented in this project

The Gateway integration suite verifies:

1. a valid client value is preserved;
2. a missing value produces a valid UUID;
3. an unsafe value is replaced;
4. the chosen value reaches the downstream HTTP server;
5. the same value is returned to the client.

The mock downstream service returns a test-only header:

```text
X-Downstream-Correlation-ID
```

This proves the value crossed the network boundary. Production services do not need
that diagnostic header.

## Manual testing

Start Event Service and Gateway, then provide an ID:

```powershell
$response = Invoke-WebRequest `
  -Uri "http://localhost:8088/api/event/getAll" `
  -Headers @{ "X-Correlation-ID" = "manual-test-123" }

$response.Headers["X-Correlation-ID"]
```

Expected value:

```text
manual-test-123
```

Test generation by omitting the header:

```powershell
$response = Invoke-WebRequest `
  -Uri "http://localhost:8088/api/event/getAll"

$response.Headers["X-Correlation-ID"]
```

Expected value: a generated UUID.

## Common mistakes

### Generating a new ID in every service

That breaks the relationship between logs. Preserve the incoming ID and generate one
only when it is missing or unsafe.

### Trusting arbitrary client input

Headers are untrusted input. Validate length and characters before putting them in
logs or forwarding them.

### Forgetting the response header

If clients cannot see the ID, support teams cannot easily ask users for it.

### Forgetting MDC cleanup

Thread reuse can attach the wrong ID to later requests.

### Assuming MDC automatically works with asynchronous code

Traditional MDC is thread-local. When work moves to another thread, explicit context
propagation or an observability framework may be required. This project currently uses
Gateway Server MVC and keeps the filter scoped to the servlet request lifecycle.

### Logging sensitive information

A correlation ID should be opaque. Do not encode usernames, email addresses, access
tokens, booking details, or other sensitive data inside it.

## Interview questions and answers

### What is a correlation ID?

A correlation ID is an identifier propagated across components participating in one
request flow so their logs and errors can be connected.

### Why generate it at the API Gateway?

The Gateway is the system entry point and can guarantee that every downstream request
has a consistent identifier.

### Why preserve a client-provided value?

Preserving a safe value lets callers connect their own logs with server-side logs.
Unsafe values should be replaced because HTTP headers are untrusted input.

### How is it propagated?

The Gateway adds it to the proxied HTTP request header. Each downstream service reads
the same header, places it in local logging context, and forwards it on further calls.

### Why use MDC?

MDC lets the logging pattern automatically attach request-scoped values to every log
statement without passing the ID through every method parameter.

### What is the main MDC risk?

MDC is commonly thread-local. It must be cleaned after the request, and asynchronous
thread changes require explicit context propagation.

### Is a correlation ID a security control?

No. It provides observability, not identity, authentication, authorization, replay
protection, or idempotency.

### When would you replace this with distributed tracing?

When the system needs standardized cross-service propagation, nested spans, latency
analysis, dependency visualization, sampling, and export to observability platforms.

## Production checklist

- [x] Preserve valid incoming IDs.
- [x] Generate an ID when missing.
- [x] Validate untrusted values.
- [x] Forward exactly one canonical header.
- [x] Return the ID to the client.
- [x] Put the ID into Gateway MDC.
- [x] Clean MDC in `finally`.
- [x] Test real downstream propagation.
- [ ] Add equivalent extraction and MDC handling to every downstream service.
- [ ] Introduce OpenTelemetry/Micrometer Tracing when full distributed tracing is needed.

## Further reading

- [Spring Boot logging correlation](https://docs.spring.io/spring-boot/reference/actuator/tracing.html)
- [Spring Boot logging properties](https://docs.spring.io/spring-boot/appendix/application-properties/index.html)

