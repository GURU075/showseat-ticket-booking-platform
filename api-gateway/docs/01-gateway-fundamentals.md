# 1. Gateway Fundamentals

## The problem before a gateway

Without a gateway, a frontend must know the location of every backend service:

```text
Frontend -> Event Service          :8081
Frontend -> Venue Service          :8082
Frontend -> Show Service           :8083
Frontend -> Seat Inventory Service :8084
```

This creates several problems:

- the frontend knows internal service addresses;
- changing a service port may require a frontend change;
- authentication, CORS, logging, and request tracing can be repeated;
- it is harder to enforce consistent rules at the system boundary.

## The system after adding a gateway

The client knows one public address:

```text
Frontend -> API Gateway :8088 -> correct internal service
```

For the first example:

```text
GET localhost:8088/api/event/getAll
                    |
                    v
        Path predicate matches /api/event/**
                    |
                    v
GET localhost:8081/api/event/getAll
```

The Event Service still owns event business logic. The gateway does not replace its
controller, service, repository, validation, or database.

## The four route concepts

A Spring Cloud Gateway route has four important parts:

1. **ID**: a unique name used to identify the route.
2. **URI**: the destination service address.
3. **Predicate**: a condition deciding whether the route matches.
4. **Filter**: an optional operation that changes the request or response.

Conceptually:

```text
Route = ID + URI + one or more predicates + zero or more filters
```

Example:

```yaml
- id: event-service-route
  uri: http://localhost:8081
  predicates:
    - Path=/api/event/**
```

This says:

> When the request path starts with `/api/event/`, forward the request to the
> application at `http://localhost:8081`.

## What a gateway should do

A gateway is a good place for cross-cutting boundary concerns such as:

- routing;
- authentication enforcement;
- correlation IDs;
- CORS;
- rate limiting;
- request and response headers;
- metrics;
- resilience policies.

## What a gateway should not do

Avoid putting domain business logic in the gateway. For example, it should not:

- create an event directly in the event database;
- calculate booking prices;
- decide whether seats are available;
- contain Event Service repositories;
- become a second implementation of service controllers.

Keeping business logic in the owning service prevents the gateway from becoming a
large, tightly coupled application.

## Gateway is a reverse proxy

The client deliberately sends a request to the gateway. The gateway then sends the
request to an internal server. This makes it a reverse proxy: it acts on behalf of
servers, while an ordinary forward proxy acts on behalf of clients.

## One important limitation

Routing does not make a failed service healthy. If Event Service is stopped, its
database is unavailable, or its controller returns an error, the gateway cannot
produce a successful Event Service response by itself.

