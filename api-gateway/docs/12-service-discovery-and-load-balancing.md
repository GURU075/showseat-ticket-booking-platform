# 12. Service Discovery and Load-Balanced Routes

## The problem with fixed URLs

The Gateway previously routed Event requests to one address:

```yaml
uri: http://localhost:8081
```

That creates three problems:

1. the Gateway must know every service host and port;
2. starting a second Event Service instance does not give it any traffic;
3. changing an instance address requires configuration changes and a restart.

Service discovery replaces the fixed address with a stable logical service ID.

## The three responsibilities

### 1. Registration

Each service starts, contacts Eureka, and registers metadata such as:

```text
service ID: event-service
host: 192.168.1.20
port: 8081
status: UP
```

The service ID comes from `spring.application.name`. Heartbeats keep the registration
alive. Eureka eventually removes an instance that stops renewing its lease.

### 2. Discovery

The Gateway asks Eureka for the currently available instances of a service ID. It does
not ask Eureka to proxy each business request; clients cache registry information.

### 3. Client-side load balancing

Spring Cloud LoadBalancer selects one discovered instance. The default algorithm is
round-robin, so requests rotate through the available instances.

```text
Request 1 -> Event instance A
Request 2 -> Event instance B
Request 3 -> Event instance A
```

## Architecture in this project

```text
Event / Venue / Show / Seat Inventory
           | register + heartbeat
           v
    Eureka Discovery Server :8761
           ^ registry lookup
           |
Client -> API Gateway :8088
           |
           | Spring Cloud LoadBalancer selects an instance
           v
      Selected downstream instance
```

The Discovery Server is in the `discovery-server` module and runs on port `8761`.
It is configured as one standalone development registry, so it neither registers with
itself nor searches for another registry peer.

## Dependencies

### `spring-cloud-starter-netflix-eureka-server`

Used only by `discovery-server`. `@EnableEurekaServer` activates the registry HTTP API
and dashboard.

### `spring-cloud-starter-netflix-eureka-client`

Used by the Gateway and all four downstream services. Presence of this starter enables
automatic registration and registry lookup; no `@EnableDiscoveryClient` annotation is
required.

### `spring-cloud-starter-loadbalancer`

Used explicitly by the Gateway. It resolves a logical service ID into a concrete
`host:port` and selects an instance.

All modules import Spring Cloud `2025.1.2`, which matches Spring Boot `4.0.7` in this
project.

## Gateway route configuration

```yaml
- id: event-service-route
  uri: lb://event-service
  predicates:
    - Path=/api/event/**
```

Meaning:

| Part | Meaning |
| --- | --- |
| `lb` | Resolve and select an instance using Spring Cloud LoadBalancer |
| `event-service` | Logical service ID to request from discovery |
| `Path` | Public requests that use this route |

The service ID must match the downstream application's name exactly:

```properties
spring.application.name=event-service
```

All configured destinations are now:

| Gateway destination | Registered application |
| --- | --- |
| `lb://event-service` | `event-service` |
| `lb://venue-service` | `venue-service` |
| `lb://show-service` | `show-service` |
| `lb://seat-inventory-service` | `seat-inventory-service` |

Paths are still preserved. Discovery changes how the host and port are selected; it
does not rewrite `/api/event/getAll`.

## Eureka client properties

Every application contains:

```properties
eureka.client.service-url.defaultZone=${EUREKA_SERVER_URL:http://localhost:8761/eureka/}
eureka.client.healthcheck.enabled=true
eureka.instance.prefer-ip-address=${EUREKA_PREFER_IP_ADDRESS:true}
```

| Property | Purpose |
| --- | --- |
| `service-url.defaultZone` | Address used for registration and registry queries |
| `healthcheck.enabled` | Publishes application health rather than only process status |
| `prefer-ip-address` | Advertises the instance IP instead of its hostname |

`defaultZone` is case-sensitive because it is a map key in Eureka configuration.
`EUREKA_SERVER_URL` must end with `/eureka/`.

For container or production environments, choose hostname versus IP according to what
other services can actually resolve and reach. Do not blindly advertise `localhost`;
inside a container it means that same container.

## Complete request flow

For this request:

```text
GET http://localhost:8088/api/event/getAll
```

the flow is:

1. CORS validates a browser origin when applicable.
2. The correlation-ID filter establishes the request ID.
3. The rate limiter consumes one Event Service token.
4. The route predicate matches `/api/event/**`.
5. `lb://event-service` asks the LoadBalancer for an instance.
6. The LoadBalancer obtains registered instances through discovery.
7. One instance is selected using round-robin.
8. The original path and query string are forwarded to that instance.
9. The circuit breaker observes the downstream result.

## Failure behavior

When Eureka knows no available instance for a service ID, the LoadBalancer produces a
`503`. The existing circuit-breaker route then returns this project's controlled
service-specific fallback:

```http
HTTP/1.1 503 Service Unavailable
Retry-After: 10
Content-Type: application/problem+json
```

The response contains the stable service error code and correlation ID. It does not
leak a LoadBalancer exception. LoadBalancer retries are disabled, preserving the rule
that Gateway POST requests are never retried automatically.

Discovery and circuit breakers are complementary:

- discovery finds candidate instances;
- load balancing selects one instance;
- a circuit breaker stops repeatedly calling a failing dependency.

## Startup order for local development

Start applications in this order:

1. PostgreSQL and Redis dependencies;
2. Discovery Server on `8761`;
3. Event, Venue, Show, and Seat Inventory services;
4. API Gateway on `8088`.

Open the Eureka dashboard:

```text
http://localhost:8761
```

Confirm these names appear as `UP` before testing Gateway routes:

```text
API-GATEWAY
EVENT-SERVICE
VENUE-SERVICE
SHOW-SERVICE
SEAT-INVENTORY-SERVICE
```

Eureka's dashboard commonly displays service IDs in uppercase even though route
configuration uses lowercase names.

## Running a second instance

Event and Venue ports are now environment-configurable. In one terminal, run Event
Service normally on `8081`. In another terminal:

```powershell
$env:SERVER_PORT = "8181"
.\mvnw.cmd spring-boot:run
```

Run that command from the `event-service` directory. Both instances use the same
`spring.application.name`, so Eureka groups them under `EVENT-SERVICE`. The dashboard
should show two instances. Repeated Gateway requests can then be served by either one.

## What is deliberately not migrated yet

This phase changes Gateway routes and registers every application. The existing
service-to-service clients inside Show Service and Seat Inventory Service still use
their current explicit base URLs. Migrating those clients to discovery-backed
`RestClient` and OpenFeign is a separate task with separate failure and test behavior.

## Tests included

`GatewayLoadBalancingTests` registers two in-process Event Service instances with the
test `SimpleDiscoveryClient`. It verifies that:

1. repeated requests reach both discovered instances;
2. a route with no registered instance returns the controlled `503` fallback.

The remaining Gateway integration tests also use `SimpleDiscoveryClient`, keeping them
independent of a real Eureka process while exercising the same `lb://` routes.

## Production considerations

The current Eureka server is intentionally a single development instance. For a
production environment consider:

- multiple Eureka peers across failure zones;
- securing the dashboard and registry endpoints;
- TLS and authentication;
- platform-native discovery when deploying to Kubernetes;
- monitoring registration count, renewal failures, and stale instances;
- graceful shutdown so terminating instances stop receiving traffic.

Do not disable Eureka self-preservation merely to make local removal appear faster.
Self-preservation helps prevent mass eviction during a network problem.

## Interview-ready explanation

> Each service registers its service ID and network location with Eureka and renews the
> registration through heartbeats. The Gateway routes to `lb://service-id`. Spring Cloud
> LoadBalancer retrieves available instances through the discovery client and selects
> one using round-robin by default. This removes fixed Gateway host and port coupling and
> supports horizontal scaling. Discovery locates instances, load balancing distributes
> calls, and circuit breakers handle failures. If no instance is available, our Gateway
> returns a controlled correlated 503 response without retrying POST requests.

## Common interview questions

### Does Eureka forward business traffic?

No. Eureka stores registration metadata. After discovery and selection, the Gateway
calls the selected service instance directly.

### Why use `lb://` instead of `http://`?

`http://` identifies one concrete destination. `lb://event-service` identifies a logical
service and allows runtime instance selection.

### What happens if one of two instances stops?

After its registration is no longer considered available, discovery stops returning it
and traffic goes to the remaining instance. There is a short detection/cache window;
removal is not instantaneous.

### Is Eureka itself a single point of failure here?

The current local server is. Production Eureka normally uses peers, and clients also
cache registry data, but high availability still requires deliberate deployment.

### Do we need `@EnableDiscoveryClient`?

No. Modern Spring Cloud activates the Eureka discovery client from the starter and
auto-configuration.

## Official references

- Spring Cloud Gateway MVC LoadBalancer filter:
  <https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webmvc/filters/loadbalancer.html>
- Spring Cloud Netflix Eureka client and server:
  <https://docs.spring.io/spring-cloud-netflix/reference/spring-cloud-netflix.html>
- Spring Cloud LoadBalancer:
  <https://docs.spring.io/spring-cloud-commons/reference/spring-cloud-commons/loadbalancer.html>
