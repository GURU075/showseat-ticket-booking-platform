# Redis Seat Locking: Features, Design, and Flow

## 1. What Redis is doing in this service

PostgreSQL and Redis now have different responsibilities:

| Storage | Responsibility | States/data |
|---|---|---|
| PostgreSQL | Durable business truth | `AVAILABLE`, `BOOKED`, `BLOCKED` |
| Redis | Temporary reservation | lock ID, owner user ID, selected seats, five-minute TTL |

A seat that is `AVAILABLE` in PostgreSQL can temporarily appear as `LOCKED` when a matching Redis key exists. A lock is not a permanent booking. Only a successful confirmation changes PostgreSQL to `BOOKED`.

This separation matches the nature of the data: bookings must survive permanently, while abandoned checkout locks should disappear automatically.

## 2. Main implemented features

### Five-minute automatic expiry

Every lock key is created with a Redis TTL. The default is configured with:

```properties
seat-lock.ttl=${SEAT_LOCK_TTL:5m}
```

Redis removes the keys after five minutes. There is no scheduler that scans the database and no background job that has to release expired locks.

### Atomic multi-seat locking

When a user selects `A1` and `A2`, the operation must produce one of only two results:

- both seats are locked; or
- neither seat is locked.

`acquire-seat-locks.lua` first checks every requested key. It writes the keys only if all seats are free. Redis executes the Lua script atomically, so another lock request cannot run in the middle of its check-and-write operation.

### Lock ownership validation

Confirm and release require all three identifiers:

```json
{
  "showId": 101,
  "userId": 501,
  "lockId": "LOCK-123"
}
```

The service checks that the lock exists and belongs to that show and user. A different user cannot confirm or release somebody else's seats.

### Safe confirmation window

Confirmation first validates the complete lock and extends its Redis TTL for a short period:

```properties
seat-lock.confirmation-ttl=${SEAT_LOCK_CONFIRMATION_TTL:1m}
```

This gives the local database booking transaction time to finish without the lock disappearing halfway through normal processing.

### Ownership-aware release

Release uses a Lua compare-and-delete operation. It deletes a key only when its current value still matches the expected lock ID and user ID.

This matters because deleting a key by name alone is unsafe. An old request must never delete a newer lock that was created for the same seat after the old one expired.

### Effective seat status when viewing seats

`GET /api/v1/show-seats/shows/{showId}` reads the permanent rows from PostgreSQL and then checks Redis for the rows whose permanent status is `AVAILABLE`.

```text
PostgreSQL AVAILABLE + Redis key exists = response LOCKED
PostgreSQL AVAILABLE + no Redis key       = response AVAILABLE
PostgreSQL BOOKED                         = response BOOKED
PostgreSQL BLOCKED                        = response BLOCKED
```

Permanent PostgreSQL status always wins. For example, a stale Redis key cannot make a booked seat available or bookable.

### Coordination with administrator blocking

Both customer locking and administrator blocking use pessimistic PostgreSQL row locks for the selected seats.

- Customer locking checks the permanent state and then writes Redis while holding the row locks.
- Blocking checks the permanent state and Redis while holding the same row locks.

Therefore, if a lock and a block request race for the same seat, only one wins. Stable sorted seat order is still used when acquiring multiple database rows to reduce deadlock risk.

### Redis failure behavior

The service fails closed when Redis is unavailable:

- it does not pretend a lock succeeded;
- it does not show seats as available when their temporary status cannot be checked;
- it returns the existing external-service error response.

After PostgreSQL has successfully committed a booking, failure to remove the Redis keys does not reverse the booking. The database remains `BOOKED`; the harmless stale keys expire automatically.

## 3. Redis keys and values

For this request:

```json
{
  "showId": 101,
  "userId": 501,
  "seatNumbers": ["A1", "A2"]
}
```

the service creates one key for each seat:

```text
seat-lock:{show:101}:seat:A1
seat-lock:{show:101}:seat:A2
```

Seat-key value:

```text
LOCK-123|501
```

It also creates a group key:

```text
seat-lock:{show:101}:group:LOCK-123
```

The group value contains the lock ID, show ID, owner, original expiry time, and complete seat list. It allows confirm/release to find and validate the whole selection from one lock ID.

The `{show:101}` portion is a Redis Cluster hash tag. It keeps all keys for this script in the same Redis Cluster slot, which allows a multi-key script to work when Redis is clustered.

## 4. Request flows

### Lock flow

```text
POST /api/v1/show-seats/lock
  -> normalize and sort seat numbers
  -> pessimistically lock the PostgreSQL seat rows
  -> reject missing, BOOKED, or BLOCKED seats
  -> Redis Lua: verify every seat key is absent
  -> Redis Lua: create all seat keys + group key with five-minute TTL
  -> return lockId and expiresAt
```

PostgreSQL remains `AVAILABLE` because the customer has not purchased the seats yet.

### Confirm flow

```text
POST /api/v1/show-seats/confirm
  -> read group key and validate show, user, and lock ID
  -> Redis Lua: validate every seat key and extend all TTLs
  -> open a short PostgreSQL transaction
  -> lock all selected rows and recheck that they are AVAILABLE
  -> change every row to BOOKED
  -> commit PostgreSQL
  -> Redis Lua: remove the owned lock keys
  -> return BOOKED
```

`SeatBookingWriter.confirm()` owns the short database transaction. Redis/network work is deliberately outside that transaction except where lock-vs-block coordination specifically needs the database row locks.

### Release flow

```text
POST /api/v1/show-seats/release
  -> validate show, user, and lock ID
  -> Redis Lua: verify every value still belongs to this lock
  -> delete the group and seat keys together
  -> return AVAILABLE
```

No PostgreSQL update is required because a temporary lock never changed the permanent row from `AVAILABLE`.

### Automatic expiry flow

```text
User locks A1
  -> Redis stores A1 with TTL 5 minutes
User abandons checkout
  -> no API request is required
TTL reaches zero
  -> Redis removes the lock keys
Next GET
  -> PostgreSQL says AVAILABLE and Redis has no key
  -> API returns AVAILABLE
```

## 5. Important classes and files

| File | Purpose |
|---|---|
| `SeatLockStore` | Business-facing temporary lock abstraction |
| `RedisSeatLockStore` | Redis implementation, key building, serialization, and error translation |
| `SeatLockProperties` | Validated TTL configuration |
| `SeatLockRedisConfig` | Reusable script and clock beans |
| `acquire-seat-locks.lua` | Atomic all-or-nothing acquisition |
| `verify-and-extend-seat-locks.lua` | Atomic ownership verification and TTL extension |
| `release-seat-locks.lua` | Atomic ownership-aware deletion |
| `SeatBookingWriter` | Short PostgreSQL transaction that changes seats to `BOOKED` |
| `SeatInventoryServiceImpl` | Orchestrates PostgreSQL permanent state and Redis temporary state |

`SeatInventoryServiceImpl` depends on `SeatLockStore`, not directly on Redis. This is dependency inversion/ports-and-adapters: business tests can use an in-memory implementation, and Redis details do not spread through the service layer.

## 6. Configuration and local run

Start Redis locally, for example with Docker:

```bash
docker run --name seat-redis -p 6379:6379 -d redis:7-alpine
```

Useful environment variables:

```text
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_DATABASE=0
REDIS_PASSWORD=
REDIS_CONNECT_TIMEOUT=2s
REDIS_COMMAND_TIMEOUT=2s
SEAT_LOCK_TTL=5m
SEAT_LOCK_CONFIRMATION_TTL=1m
```

Then run the service and use this test flow:

```http
POST /api/v1/show-seats/lock
Content-Type: application/json

{
  "showId": 101,
  "userId": 501,
  "seatNumbers": ["A1", "A2"]
}
```

Copy the returned `lockId`, then confirm:

```http
POST /api/v1/show-seats/confirm
Content-Type: application/json

{
  "showId": 101,
  "userId": 501,
  "lockId": "COPY-THE-RETURNED-LOCK-ID"
}
```

To study expiry, lock the seats, do not confirm or release, wait five minutes, and call the GET endpoint again.

## 7. Tests implemented

- service tests for the Redis status overlay;
- atomic lock orchestration tests;
- ownership failure tests;
- confirmation ordering tests;
- Redis-cleanup failure test after a successful booking;
- block-vs-Redis-lock tests;
- booking writer validation tests;
- concurrent lock-vs-lock integration test;
- concurrent lock-vs-block integration test.
- optional real-Redis integration test for the Lua acquire, conflict, ownership, extend, and release behavior.

The normal automated test suite uses an in-memory `SeatLockStore` test double for concurrency testing, so developers do not need a running Redis instance merely to run unit tests. The production application still uses `RedisSeatLockStore`.

To execute the optional real-Redis test, use an isolated Redis database and run:

```powershell
$env:RUN_REDIS_TESTS="true"
.\mvnw.cmd -Dtest=RedisSeatLockStoreIntegrationTests test
```
