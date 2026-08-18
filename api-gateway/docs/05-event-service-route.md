# 5. Guided Event Service Route

This chapter configures one route. It does not configure routes for the other
services.

## Step 1: Confirm the downstream contract

The existing Event Service uses:

```properties
server.port=8081
```

Its controller base mapping is:

```java
@RequestMapping("/api/event")
```

One endpoint is:

```java
@GetMapping("/getAll")
```

Therefore the direct URL is:

```text
GET http://localhost:8081/api/event/getAll
```

Always determine the real downstream port and controller path before writing a route.

## Step 2: Choose the public contract

For the first exercise, keep the same path and change only the port:

```text
Public:   http://localhost:8088/api/event/getAll
Internal: http://localhost:8081/api/event/getAll
```

Because the paths are identical, no path-rewrite filter is needed.

## Step 3: Rename the configuration file

Inside `src/main/resources`, rename:

```text
application.properties
```

to:

```text
application.yml
```

Renaming is for readability, not because Gateway requires YAML.

## Step 4: Add the minimal configuration

Put this in `application.yml`:

```yaml
spring:
  application:
    name: api-gateway

  cloud:
    gateway:
      server:
        webmvc:
          routes:
            - id: event-service-route
              uri: ${EVENT_SERVICE_URL:http://localhost:8081}
              predicates:
                - Path=/api/event/**

server:
  port: 8088
```

## Step 5: Read the configuration as a sentence

The route means:

> In the `api-gateway` application running on port `8088`, if an incoming path matches
> `/api/event/**`, use the route named `event-service-route` and forward the request to
> the URL from `EVENT_SERVICE_URL`, or to `http://localhost:8081` when that variable is
> absent.

If you cannot explain the route in a sentence like this, pause before adding more
routes.

## Step 6: Understand the equivalent properties version

If you decide not to use YAML, this is equivalent:

```properties
spring.application.name=api-gateway
server.port=8088

spring.cloud.gateway.server.webmvc.routes[0].id=event-service-route
spring.cloud.gateway.server.webmvc.routes[0].uri=${EVENT_SERVICE_URL:http://localhost:8081}
spring.cloud.gateway.server.webmvc.routes[0].predicates[0]=Path=/api/event/**
```

Use either the YAML block or the properties block, not duplicate definitions in both.

## Step 7: Predict requests before running

Try to predict each result:

| Gateway request | Should route? | Downstream request |
| --- | --- | --- |
| `GET /api/event/getAll` | Yes | `GET :8081/api/event/getAll` |
| `GET /api/event/get/abc` | Yes | `GET :8081/api/event/get/abc` |
| `GET /api/event/search?keyword=rock` | Yes | Query parameter is preserved |
| `POST /api/event/create` | Yes | Body and method are forwarded |
| `GET /api/venue/getAll` | No | No Venue route exists yet |

## Step 8: Keep the Java application simple

No routing Java code is required for this example. The generated main class remains:

```java
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
```

Spring Boot auto-configuration reads the route configuration and creates the required
Gateway components.

## Checkpoint questions

Before moving on, answer these without looking at the configuration:

1. Which port receives the client request?
2. Which route field contains the downstream server address?
3. Which field decides whether `/api/event/getAll` matches?
4. Why is there no `RewritePath` or `StripPrefix` filter?
5. What default is used when `EVENT_SERVICE_URL` is not defined?

Answers:

1. Gateway port `8088`.
2. `uri`.
3. The `Path=/api/event/**` predicate.
4. The public and downstream paths are identical.
5. `http://localhost:8081`.

