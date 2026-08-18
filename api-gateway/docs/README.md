# API Gateway Learning Guide

This guide teaches Spring Cloud Gateway using the ShowSeat ticket-booking project.
It is written for someone who understands the theory of an API Gateway but has not
configured one before.

The generated `api-gateway` currently uses **Spring Cloud Gateway Server Web MVC**.
All working configuration in this guide therefore uses this prefix:

```text
spring.cloud.gateway.server.webmvc
```

Do not copy configuration that uses `server.webflux` unless the Maven dependency is
also changed to the WebFlux gateway starter.

## Recommended reading order

1. [Gateway fundamentals](01-gateway-fundamentals.md)
2. [YAML versus properties](02-yaml-vs-properties.md)
3. [Project structure and dependencies](03-project-and-dependencies.md)
4. [Understanding a route](04-route-anatomy.md)
5. [Guided Event Service route](05-event-service-route.md)
6. [Running, testing, and troubleshooting](06-testing-and-troubleshooting.md)
7. [Exercises and next steps](07-exercises-and-next-steps.md)
8. [Correlation-ID filter](08-correlation-id-filter.md)
9. [Downstream circuit breakers, fallbacks, and safe retry policy](09-event-service-resilience.md)
10. [CORS policy, preflight requests, and browser security](10-cors.md)

## Learning approach

The guide teaches routing through the Event Service example first. The running project
also routes Venue, Show, and Seat Inventory APIs, with integration tests protecting
each route contract.

## Project ports used in this guide

| Application | Port | Purpose |
| --- | ---: | --- |
| API Gateway | `8088` | Public entry point for clients |
| Event Service | `8081` | Event business operations |
| Venue Service | `8082` | Venue, screen, city, and physical-seat catalog |
| Show Service | `8083` | Scheduled shows |
| Seat Inventory Service | `8084` | Per-show seat availability and locking |

## Configured route table

| Public path | Destination |
| --- | --- |
| `/api/event/**` | Event Service |
| `/api/v1/cities/**` | Venue Service |
| `/api/v1/venues/**` | Venue Service |
| `/api/v1/screens/**` | Venue Service |
| `/api/v1/venue-seats/**` | Venue Service physical-seat catalog |
| `/api/v1/shows/**` | Show Service |
| `/api/v1/show-seats/**` | Seat Inventory Service |

The Event Service controller uses the base path `/api/event`, so its existing direct
endpoint is:

```text
GET http://localhost:8081/api/event/getAll
```

After the first gateway route works, the client-facing endpoint will be:

```text
GET http://localhost:8088/api/event/getAll
```
