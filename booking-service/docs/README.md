# Booking Service learning guide

Read these notes in order:

1. [Architecture and create-booking flow](01-architecture-and-create-flow.md)
2. [Idempotency, transactions, and compensation](02-idempotency-transactions-and-compensation.md)
3. [Running, testing, and API examples](03-running-testing-and-api.md)

## Scope implemented now

This phase implements one complete vertical slice:

- create a booking;
- retrieve a booking by ID;
- validate that the show is bookable;
- acquire an expiring seat lock;
- calculate and persist the amount;
- make client retries idempotent;
- compensate by releasing the seat lock if persistence fails;
- register with Eureka and receive traffic through the Gateway.

It intentionally does **not** implement payment, confirmation, cancellation, or
expiry processing. Those features require their own explicit state transitions
and reliability rules.
