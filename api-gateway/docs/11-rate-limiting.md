# 11. Rate Limiting with Token Buckets

## The problem

Without rate limiting, one client can send thousands of requests and consume Gateway
threads, downstream connections, database capacity, and circuit-breaker call volume.
A genuine traffic spike and a badly written client retry loop can cause the same damage
as an intentional attack.

Rate limiting gives every client a bounded request allowance. When that allowance is
empty, the Gateway rejects the request before it reaches a downstream service.

Rate limiting is not authentication, authorization, DDoS protection, or idempotency.
Those solve different problems.

## Where it runs in this project

```text
Browser CORS check
    -> correlation-ID filter
    -> rate-limit filter
        -> allowed: route + circuit breaker -> downstream service
        -> denied: controlled 429 response; downstream is not called
```

The filter covers only the public paths that are already in the route table. Actuator,
unmatched paths, direct internal fallback URLs, and CORS preflight requests do not use
tokens.

## The token-bucket idea

Imagine that Event Service has a bucket containing 60 tokens:

1. Each request costs one token.
2. The client can make a burst of up to 60 requests.
3. Tokens refill continuously at 60 tokens per minute.
4. The bucket never holds more than its capacity of 60.
5. A request arriving when no token is available receives `429 Too Many Requests`.

`capacity` controls the permitted burst. `refill-tokens` and `refill-period` control the
long-term rate. They are separate so a policy could allow a burst of 20 while adding
only 10 tokens each second.

## How a client is identified

The bucket key is:

```text
service ID + authenticated principal
```

When there is no authenticated principal yet, the implementation falls back to:

```text
service ID + request.getRemoteAddr()
```

Therefore one client's Event Service traffic does not consume its Show Service quota.
Different clients also receive different buckets.

The filter deliberately does not trust `X-Forwarded-For` from any caller. In production
behind a reverse proxy, configure trusted forwarded-header handling at the infrastructure
boundary, or prefer the authenticated user/account as the key. Otherwise clients can
spoof an IP header and avoid the limit.

## Dependencies

### Bucket4j

`bucket4j_jdk17-core` provides the thread-safe token-bucket algorithm. The selected
`8.15.0` version matches the optional version used by Spring Cloud Gateway Server MVC
`5.0.2`.

### Caffeine

Caffeine stores active client buckets in memory. The cache has a maximum size and an
idle expiration, preventing unused client keys from growing memory forever.

This storage is suitable for the current single Gateway instance. It is not a global
production limit when several Gateway replicas are running; each replica would have
its own allowance. Use a supported distributed Bucket4j store or an infrastructure
rate limiter for a multi-instance deployment.

## Configuration explained

```yaml
gateway:
  rate-limit:
    enabled: true
    max-buckets: 10000
    bucket-expiration: 10m
    policies:
      event-service:
        capacity: 60
        refill-tokens: 60
        refill-period: 1m
```

| Property | Meaning |
| --- | --- |
| `enabled` | Emergency switch that bypasses rate limiting when `false` |
| `max-buckets` | Maximum cached client-and-service buckets; bounds memory use |
| `bucket-expiration` | Removes a bucket after that period without access |
| `capacity` | Maximum tokens and maximum immediate burst |
| `refill-tokens` | Tokens returned during the refill period |
| `refill-period` | Time over which refill tokens become available |

All values can be changed with the environment variables shown in `application.yml`.
The application validates positive cache, duration, capacity, and refill values during
startup so a broken policy fails fast.

Current defaults are deliberately starter values, not traffic-tested production SLOs:

| Service | Capacity | Refill |
| --- | ---: | --- |
| Event Service | 10 | 10/minute |
| Venue Service | 120 | 120/minute |
| Show Service | 120 | 120/minute |
| Seat Inventory Service | 120 | 120/minute |

Choose real limits from load tests, endpoint cost, user behavior, and service capacity.

## Response headers

Every matched API response contains:

```text
X-RateLimit-Limit: 60
X-RateLimit-Remaining: 59
```

When the bucket is empty, the Gateway returns:

```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/problem+json
Cache-Control: no-store
Retry-After: 1
X-RateLimit-Limit: 60
X-RateLimit-Remaining: 0
X-Correlation-ID: checkout-123
```

The problem body contains a stable `RATE_LIMIT_EXCEEDED` code, the original public
path, and correlation ID. `Retry-After` is rounded up to whole seconds so a client does
not retry before the next token is available. The CORS policy exposes these headers to
approved browser origins.

## Interaction with other Gateway features

- CORS preflight is a browser permission check, not a business request, so it costs no
  token.
- A rejected request keeps its correlation ID for logs and support investigations.
- Rate limiting runs before routing, so a rejected request does not call downstream.
- A rejected request does not count as a circuit-breaker failure.
- A downstream `503` still consumes a token because the Gateway performed real work.
- A `429` does not make POST safe to retry. Idempotency is still needed for duplicate
  write protection.

## Tests included

`GatewayRateLimitingTests` verifies that:

1. requests within capacity reach downstream;
2. the next request returns controlled `429` with the expected metadata;
3. rejected requests do not call downstream;
4. service quotas are independent;
5. CORS preflight consumes no token;
6. Actuator is excluded and the internal error endpoint cannot be called directly.

`GatewayRateLimitPropertiesTests` verifies startup validation for unsafe configuration.

## Interview-ready explanation

> We rate-limit at the API Gateway to shed excessive traffic before it consumes
> downstream capacity. We use a token bucket because it permits a controlled burst while
> enforcing a sustainable refill rate. Buckets are isolated by service and client. The
> current Caffeine store is bounded and appropriate for one instance; multiple replicas
> require distributed state or an infrastructure-level limiter. Exhausted requests return
> a non-cacheable RFC problem response with 429, Retry-After, remaining quota, and the
> correlation ID, without calling downstream or affecting circuit-breaker state.

## Common interview questions

### Why return 429 rather than 503?

`429` means this client exceeded an allowed request rate. `503` means the server or a
dependency is temporarily unavailable. Clients may use different retry behavior for
each condition.

### Why not use one global bucket?

A global bucket lets one noisy client block everyone and lets traffic to one service
starve unrelated services. Per-client, per-service buckets give better isolation.

### Why is `Retry-After` important?

It tells a cooperative client how long to wait. Without it, immediate retries can create
a retry storm and keep the bucket empty.

### What changes after authentication is implemented?

Prefer a stable account, tenant, API key, or authenticated principal over source IP.
Several real users can share one NAT IP, while one malicious client may rotate IPs.

### Is local Caffeine storage production-ready?

Only for a single Gateway instance or a deliberately approximate per-instance limit.
With multiple replicas, a client can receive an allowance from every replica and limits
reset when an instance restarts.

## Manual test

Temporarily start the Gateway with a small Event allowance:

```powershell
$env:EVENT_RATE_LIMIT_CAPACITY = "2"
$env:EVENT_RATE_LIMIT_REFILL_TOKENS = "2"
$env:EVENT_RATE_LIMIT_REFILL_PERIOD = "1m"
.\mvnw.cmd spring-boot:run
```

Call the Event route three times from the same client. The first two should pass; the
third should return `429`. Remove those environment variables afterward to restore the
defaults.

## Source reference

Spring Cloud Gateway Server Web MVC documents Bucket4j's token-bucket rate limiter and
explains that client-key resolvers are configured through Java rather than external
properties: <https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webmvc/filters/ratelimiter.html>
