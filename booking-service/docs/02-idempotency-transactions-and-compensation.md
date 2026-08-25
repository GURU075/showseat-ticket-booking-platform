# Idempotency, transactions, and compensation

## The duplicate-booking problem

Suppose the server creates a booking but the response is lost. The client does
not know whether creation succeeded and sends the same POST again. Without
idempotency, the retry could create a second booking and lock more seats.

## How the implementation prevents it

The client sends a stable header:

```http
Idempotency-Key: checkout-user10-show20-attempt1
```

Booking Service stores a unique pair of `user_id` and `idempotency_key`. It also
stores a SHA-256 fingerprint of the meaningful request fields.

| Situation | Result |
|---|---|
| New key | Create the booking and return `201` |
| Same key and same request after success | Return the original booking with `200` |
| Same key but different request | Return `409 IDEMPOTENCY_KEY_REUSED` |
| Same key while the first request is running | Return `409 BOOKING_CREATION_IN_PROGRESS` |

An idempotent replay includes `Idempotency-Replayed: true`. The Gateway exposes
that header to approved browser origins.

The database unique constraint is the final concurrency guard. An in-memory map
would fail as soon as multiple Booking Service instances were started.

## Transaction boundaries

1. Transaction A inserts a lightweight `CREATING` booking and commits.
2. No transaction is open while Show and Seat Inventory are called.
3. Transaction B fills the price and lock data, then changes status to `PENDING`.

`@Version` adds optimistic locking so future confirmation, cancellation, and
expiry updates cannot silently overwrite one another.

## Compensation

PostgreSQL and the Redis seat lock cannot share a normal ACID transaction.
If the lock succeeds but saving the pending booking fails, Booking Service calls
Seat Inventory's release endpoint.

```text
seat lock succeeds -> database completion fails -> release lock -> remove CREATING row
```

The release is best effort. If Seat Inventory is unavailable during compensation,
the lock's Redis TTL still releases the seats eventually. In a more advanced
production phase, add an outbox/reconciliation worker so failed compensation is
also retried durably.

## Why automatic retries are disabled

Feign uses `Retryer.NEVER_RETRY`. Automatically retrying a state-changing request
can duplicate work when the caller cannot tell whether the first attempt reached
the downstream service. Retries must be explicit and protected by an idempotency
contract.

## Interview summary

Idempotency makes repeated equivalent requests have the same business effect.
Here it is implemented with a client key, a request fingerprint, and a database
unique constraint. Because the workflow spans PostgreSQL and a Redis-backed
service, it uses short local transactions plus compensation rather than pretending
there is one distributed ACID transaction.
