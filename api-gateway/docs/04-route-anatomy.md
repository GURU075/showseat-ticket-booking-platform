# 4. Understanding a Route

Use this route as the reference:

```yaml
spring:
  cloud:
    gateway:
      server:
        webmvc:
          routes:
            - id: event-service-route
              uri: ${EVENT_SERVICE_URL:http://localhost:8081}
              predicates:
                - Path=/api/event/**
```

## Route ID

```yaml
id: event-service-route
```

The ID uniquely names the route inside Gateway. It is useful in startup information,
Actuator output, logs, metrics, and tests.

Good IDs describe destinations or purposes:

```text
event-service-route
public-event-api
```

Avoid vague IDs such as `route1` because they become difficult to understand after
more routes are added.

## Destination URI

```yaml
uri: ${EVENT_SERVICE_URL:http://localhost:8081}
```

The URI identifies the downstream server. It consists of:

```text
scheme://host:port
http://localhost:8081
```

- `http` is the protocol;
- `localhost` is the host;
- `8081` is the Event Service port.

The URI is a base address, not the complete endpoint. Gateway preserves the incoming
path unless a filter changes it.

## Path predicate

```yaml
predicates:
  - Path=/api/event/**
```

A predicate decides whether the route is eligible. This Path predicate matches all
requests whose paths begin with `/api/event/`.

| Incoming path | Matches? | Reason |
| --- | --- | --- |
| `/api/event/getAll` | Yes | Begins with `/api/event/` |
| `/api/event/get/abc` | Yes | `**` accepts remaining segments |
| `/api/event/search?keyword=music` | Yes | Matching uses the path; the query string is forwarded |
| `/api/venue/getAll` | No | Different prefix |
| `/event/getAll` | No | Missing `/api` |

The predicate does not restrict the HTTP method. GET, POST, PUT, and DELETE requests
matching this path can all use the route. You could add a Method predicate later if
there were a real requirement to restrict methods.

## Filters

Filters are optional. They can modify the request before forwarding or the response
before returning it to the client.

Common examples include:

- adding or removing headers;
- rewriting a path;
- stripping a path prefix;
- retrying selected failures;
- applying a circuit breaker;
- rate limiting.

The first Event route intentionally has no filters:

```text
Incoming path:   /api/event/getAll
Forwarded path:  /api/event/getAll
```

This matches the existing Event Controller base path, so rewriting would add needless
complexity.

## Complete request construction

For this incoming request:

```text
GET http://localhost:8088/api/event/search?keyword=music
```

Gateway combines:

```text
Route URI:      http://localhost:8081
Preserved path: /api/event/search
Query string:   ?keyword=music
```

The downstream request becomes:

```text
GET http://localhost:8081/api/event/search?keyword=music
```

## What happens when nothing matches?

If a request does not match any route and Gateway has no local handler for it, the
client normally receives `404 Not Found`.

Example:

```text
GET http://localhost:8088/api/v1/unknown
```

That 404 is expected because no configured route owns `/api/v1/unknown`.

## What happens when the destination is unavailable?

The route can match correctly while forwarding still fails. If Event Service is not
running on port `8081`, Gateway will return a server-side error such as `500` or `503`,
depending on the failure and configuration.

This distinction is useful:

```text
404: often no route/path matched
5xx: route may have matched, but downstream communication failed
```
