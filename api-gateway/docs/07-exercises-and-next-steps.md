# 7. Exercises and Next Steps

Complete these exercises only after the Event Service route works.

## Exercise 1: Translate YAML to properties

Without copying from Chapter 2, translate this YAML:

```yaml
server:
  port: 8088
```

Then translate the first route. Pay attention to the two list indexes.

## Exercise 2: Explain the request flow

Explain this request aloud:

```text
GET http://localhost:8088/api/event/get/abc
```

Your explanation should mention:

- gateway port;
- predicate match;
- route ID;
- destination URI;
- preserved path;
- Event Service response.

## Exercise 3: Test query-string forwarding

Call:

```text
GET http://localhost:8088/api/event/search?keyword=music
```

Confirm that the `keyword` query parameter reaches Event Service without a special
Gateway filter.

## Exercise 4: Verify the remaining route contracts

After understanding the Event route, inspect the remaining controllers and verify:

1. Venue catalog seats use `/api/v1/venue-seats/**`;
2. Show Service uses `/api/v1/shows/**`;
3. per-show inventory uses `/api/v1/show-seats/**`;
4. the removed ambiguous `/api/v1/seats` path returns 404.

Compare each result with the integration tests in `ApiGatewayApplicationTests`.

## Exercise 5: Add a second predicate only for learning

Research the `Method` predicate and consider this question:

> Would restricting a general Event Service route to GET accidentally block create,
> update, and delete endpoints?

The answer is yes. Predicates on one route are combined as conditions, so every added
predicate should represent an actual API requirement.

## Features to learn later

Add these in small stages rather than all at once:

1. authentication and authorization;
2. Docker-specific infrastructure and environment configuration.

## Suggested review request

After you implement and test the first route, ask for this review:

```text
Review my API Gateway Event Service route. Check dependency compatibility, the MVC
configuration namespace, YAML structure, path matching, destination configuration,
and whether internal paths are leaked. Explain problems but do not fix them yet.
```

## Completion checklist

- [ ] I can explain why YAML is convenient but not required.
- [ ] I can translate one route between YAML and properties.
- [ ] I know whether my project uses Gateway MVC or Gateway WebFlux.
- [ ] I can identify a route ID, URI, predicate, and filter.
- [ ] Event Service works directly on port `8081`.
- [ ] Event Service works through Gateway on port `8088`.
- [ ] An unrelated path returns 404 through Gateway.
- [ ] I can explain why the first route does not need path rewriting.
- [ ] I can explain why Venue seats and per-show inventory use different paths.
- [ ] I can explain correlation-ID propagation and why MDC must be cleaned.
- [ ] I can explain the Event Service circuit-breaker states and controlled fallback.
- [ ] I can explain why POST requests are not automatically retried.
- [ ] I can explain CORS preflight and why CORS is not authentication.
- [ ] I can explain token-bucket capacity, refill, `429`, and `Retry-After`.
- [ ] I know why an in-memory limiter is not global across Gateway replicas.
- [ ] I can explain registration, discovery, `lb://`, and client-side load balancing.
- [ ] I know what happens when no healthy service instance is registered.
