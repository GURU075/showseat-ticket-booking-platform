# Redis Seat Lock Implementation - Detailed Flow

## 1. Purpose of this document

This guide explains how the Redis implementation works from the HTTP API down to Redis and PostgreSQL. It is intended for learning, debugging, and interview preparation.

The central design rule is:

```text
PostgreSQL stores permanent seat state.
Redis stores temporary seat-lock ownership.
```

PostgreSQL stores:

```text
AVAILABLE
BOOKED
BLOCKED
```

Redis temporarily represents:

```text
LOCKED
```

Therefore, locking a seat does not update its PostgreSQL status to `LOCKED`. The API calculates the visible status by combining the PostgreSQL row with the Redis key.

## 2. High-level architecture

```text
HTTP request
    |
    v
SeatInventoryController
    |
    v
SeatInventoryServiceImpl
    |
    +--------------------------+
    |                          |
    v                          v
ShowSeatRepository        SeatLockStore
    |                          |
    v                          v
PostgreSQL              RedisSeatLockStore
                               |
                               v
                       StringRedisTemplate
                               |
                               v
                         Redis + Lua
```

The service layer depends on `SeatLockStore`, which is an interface. It does not depend directly on `RedisSeatLockStore`, `StringRedisTemplate`, Redis keys, or Lua syntax.

## 3. Files involved

### API and orchestration

| File | Responsibility |
|---|---|
| `SeatInventoryController` | Receives lock, confirm, release, view, block, and unblock HTTP requests |
| `SeatInventoryServiceImpl` | Coordinates business rules, PostgreSQL, and temporary locks |
| `SeatBookingWriter` | Owns the short PostgreSQL transaction that changes seats to `BOOKED` |

### Redis abstraction and implementation

| File | Responsibility |
|---|---|
| `SeatLockStore` | Business contract for temporary lock operations |
| `RedisSeatLockStore` | Production Redis implementation of that contract |
| `SeatLockDetails` | Immutable lock information returned by the store |

### Configuration

| File | Responsibility |
|---|---|
| `SeatLockProperties` | Reads and validates lock TTL configuration |
| `SeatLockRedisConfig` | Creates the UTC clock and loads Lua script beans |
| `SeatLockScripts` | Groups the three loaded `RedisScript<Long>` objects |
| `application.properties` | Contains Redis connection and TTL configuration |

### Lua resources

| File | Responsibility |
|---|---|
| `acquire-seat-locks.lua` | Atomically checks and creates the complete lock |
| `verify-and-extend-seat-locks.lua` | Atomically verifies ownership and extends all TTLs |
| `release-seat-locks.lua` | Atomically verifies ownership and deletes all keys |

### Database migration

| File | Responsibility |
|---|---|
| `V2__release_legacy_database_locks.sql` | Releases old database locks and prevents new `LOCKED` rows in PostgreSQL |

## 4. Configuration flow

The configuration begins in `application.properties`:

```properties
spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.port=${REDIS_PORT:6379}
spring.data.redis.database=${REDIS_DATABASE:0}
spring.data.redis.password=${REDIS_PASSWORD:}
spring.data.redis.connect-timeout=${REDIS_CONNECT_TIMEOUT:2s}
spring.data.redis.timeout=${REDIS_COMMAND_TIMEOUT:2s}

seat-lock.ttl=${SEAT_LOCK_TTL:5m}
seat-lock.confirmation-ttl=${SEAT_LOCK_CONFIRMATION_TTL:1m}
```

The syntax `${NAME:default}` means:

```text
Use environment variable NAME when it exists.
Otherwise, use the value after the colon.
```

For example:

```text
SEAT_LOCK_TTL is not set -> use 5m
SEAT_LOCK_TTL=2m         -> use 2m
```

Spring Boot uses the `spring.data.redis.*` properties to create a Redis connection factory and a `StringRedisTemplate` bean.

`SeatLockProperties` binds the two application-specific properties:

```text
seat-lock.ttl              -> Duration ttl
seat-lock.confirmation-ttl -> Duration confirmationTtl
```

It rejects missing, zero, or negative durations when the application starts.

## 5. Script-loading flow

`SeatLockRedisConfig` creates two Spring beans.

### UTC clock

```java
@Bean
Clock applicationClock() {
    return Clock.systemUTC();
}
```

`RedisSeatLockStore` uses this clock to calculate the `expiresAt` returned by the API. Injecting a clock also allows tests to use a fixed time.

### Lua scripts

```java
@Bean
SeatLockScripts seatLockScripts() {
    return new SeatLockScripts(
        script("redis/acquire-seat-locks.lua"),
        script("redis/verify-and-extend-seat-locks.lua"),
        script("redis/release-seat-locks.lua")
    );
}
```

The files are under `src/main/resources`, so Maven places them on the application classpath. `ClassPathResource` can load them inside the IDE, from `target/classes`, or from the packaged JAR.

`RedisScript<Long>` means that Spring should convert the Lua result to Java `Long`:

```text
Lua return 1 -> Java Long 1
Lua return 0 -> Java Long 0
```

The script objects are prepared at application startup, but Redis executes them only when an API operation needs them.

## 6. The `SeatLockStore` contract

```java
public interface SeatLockStore {
    SeatLockDetails acquire(Long showId, Long userId, List<String> seatNumbers);

    Set<String> findLockedSeatNumbers(
        Long showId,
        Collection<String> seatNumbers
    );

    SeatLockDetails verifyAndExtend(
        Long showId,
        Long userId,
        String lockId
    );

    SeatLockDetails release(Long showId, Long userId, String lockId);
}
```

The four responsibilities are:

```text
acquire               -> create one all-or-nothing selection lock
findLockedSeatNumbers -> discover effective LOCKED seats
verifyAndExtend       -> protect a live lock during confirmation
release               -> safely remove an owned lock
```

`RedisSeatLockStore` is the production implementation. `InMemorySeatLockStore` is a synchronized test implementation used by automated tests without requiring Redis.

## 7. Data stored in Redis

Suppose this request is received:

```json
{
  "showId": 101,
  "userId": 501,
  "seatNumbers": ["A1", "A2"]
}
```

Assume the generated lock ID is `LOCK-123`.

### Individual seat keys

```text
Key:   seat-lock:{show:101}:seat:A1
Value: LOCK-123|501
TTL:   300 seconds

Key:   seat-lock:{show:101}:seat:A2
Value: LOCK-123|501
TTL:   300 seconds
```

These keys make availability checks efficient. If the `A1` key exists, `A1` currently has a temporary lock.

### Group key

```text
Key: seat-lock:{show:101}:group:LOCK-123
```

Its value is encoded in this order:

```text
lockId|showId|userId|expiresEpochMillis|comma-separated-seat-numbers
```

Example:

```text
LOCK-123|101|501|1783952700000|A1,A2
```

The group key allows the server to start with a `lockId` and recover the complete trusted selection and owner.

### Why both key types are needed

```text
Seat key  -> Is this individual seat locked?
Group key -> Which seats and owner belong to this lock ID?
```

### Redis Cluster hash tag

The shared portion inside braces is:

```text
{show:101}
```

Redis Cluster places keys with the same hash tag in the same hash slot. This allows one Lua script to access all keys for a show selection.

## 8. Detailed lock flow

Endpoint:

```http
POST /api/v1/seats/lock
```

Request:

```json
{
  "showId": 101,
  "userId": 501,
  "seatNumbers": ["A2", "A1"]
}
```

### Step 1: controller validation

`SeatInventoryController` applies `@Valid` to the request.

Validation checks include:

- positive `showId`;
- positive `userId`;
- at least one seat;
- no more than 20 seats.

### Step 2: normalize and sort seats

`SeatInventoryServiceImpl.lockSeats()` converts the seat numbers to a stable form:

```text
["A2", " A1 "] -> ["A1", "A2"]
```

It also rejects invalid or duplicate seat numbers.

Sorting is useful because concurrent transactions lock database rows in the same order, reducing deadlock risk.

### Step 3: lock and validate PostgreSQL rows

The repository loads the requested rows using a pessimistic database lock.

The service checks:

- every requested seat exists;
- every seat's permanent status is `AVAILABLE`;
- no seat is already `BOOKED` or `BLOCKED`.

The database rows are not changed to `LOCKED`.

### Step 4: call the temporary-lock port

```java
SeatLockDetails lock = seatLockStore.acquire(
    request.showId(),
    request.userId(),
    requestedNumbers
);
```

At runtime, Spring injects `RedisSeatLockStore`, so this calls the Redis implementation.

### Step 5: build the lock in Java

`RedisSeatLockStore.acquire()`:

1. generates a UUID-based `LOCK-...` ID;
2. gets the current UTC time from `Clock`;
3. adds the configured five-minute TTL;
4. creates `SeatLockDetails`;
5. creates all Redis key names.

### Step 6: execute the acquire Lua script

Java sends:

```text
KEYS = [A1 seat key, A2 seat key, group key]

ARGV[1] = LOCK-123|501
ARGV[2] = encoded group value
ARGV[3] = 300000
```

The script:

```text
checks every key
    |
    +-- any exists -> return 0 and write nothing
    |
    +-- none exists -> create every key with PX 300000
```

Because Redis executes the Lua script atomically, another request cannot run between its check and write phases.

### Step 7: interpret the result

```text
result 1 -> lock created successfully
result 0 -> throw SeatConflictException
null     -> throw ExternalServiceException
```

### Step 8: return API response

```json
{
  "lockId": "LOCK-123",
  "showId": 101,
  "seatNumbers": ["A1", "A2"],
  "status": "LOCKED",
  "expiresAt": "2026-08-13T10:05:00Z"
}
```

State after the response:

```text
PostgreSQL A1 = AVAILABLE
PostgreSQL A2 = AVAILABLE
Redis A1 key  = exists
Redis A2 key  = exists
API result    = LOCKED
```

## 9. Detailed view-seats flow

Endpoint:

```http
GET /api/v1/seats/shows/101
```

### Step 1: read permanent rows

PostgreSQL might return:

```text
A1 AVAILABLE
A2 AVAILABLE
A3 BOOKED
A4 BLOCKED
```

### Step 2: select only permanently available seats

Only these need a Redis lookup:

```text
A1, A2
```

There is no reason for a Redis lock to override `BOOKED` or `BLOCKED`.

### Step 3: perform Redis multi-get

`findLockedSeatNumbers()` creates the two Redis key names and calls `multiGet()`.

Example Redis result:

```text
A1 -> LOCK-123|501
A2 -> null
```

The method returns:

```java
Set.of("A1")
```

### Step 4: calculate effective status

```text
A1: DB AVAILABLE + Redis key exists -> LOCKED
A2: DB AVAILABLE + no Redis key     -> AVAILABLE
A3: DB BOOKED                       -> BOOKED
A4: DB BLOCKED                      -> BLOCKED
```

Redis does not change the entity. It changes only the status returned in the API response.

## 10. Automatic expiry flow

The acquisition Lua script creates each key with:

```lua
SET key value PX 300000
```

`PX 300000` means expire this key after 300,000 milliseconds.

If the customer does nothing:

```text
Lock created
    |
    v
TTL counts down inside Redis
    |
    v
TTL reaches zero
    |
    v
Redis treats the key as expired and removes it
```

No Java scheduler runs and no PostgreSQL update is needed. PostgreSQL was already `AVAILABLE`.

On the next GET request:

```text
DB status = AVAILABLE
Redis key = absent
API status = AVAILABLE
```

## 11. Detailed confirmation flow

Endpoint:

```http
POST /api/v1/seats/confirm
```

Request:

```json
{
  "showId": 101,
  "userId": 501,
  "lockId": "LOCK-123"
}
```

### Step 1: read the group key

`RedisSeatLockStore.verifyAndExtend()` builds:

```text
seat-lock:{show:101}:group:LOCK-123
```

If the key is absent, the lock expired, was released, or never existed. The service returns a not-found error.

### Step 2: decode and validate metadata

The group value is converted back into `SeatLockDetails`.

The implementation checks:

- stored show ID equals request show ID;
- stored lock ID equals request lock ID;
- stored user ID equals request user ID;
- the group metadata is correctly formatted.

A different user receives a conflict and cannot confirm the lock.

### Step 3: atomically verify and extend

The Lua script checks:

```text
Does the group value exactly match?
Does every seat value exactly equal LOCK-123|501?
```

If every comparison succeeds, it sets every key's remaining TTL to the configured confirmation TTL, currently one minute.

This is a reset to one minute, not an extra minute added to the old TTL.

Verification and extension must be atomic. Separate commands could allow the lock to expire between checking it and extending it.

### Step 4: start a short PostgreSQL transaction

After Redis validation, `SeatBookingWriter.confirm()` opens the database transaction.

It:

1. pessimistically locks all selected database rows;
2. confirms that every seat still exists;
3. confirms that every permanent state is still `AVAILABLE`;
4. calls `ShowSeat.book()` for every seat;
5. commits the transaction.

The recheck prevents stale Redis information from overriding a permanent database conflict.

### Step 5: remove the temporary Redis lock

After the database commits, the service calls `seatLockStore.release()`.

The release script compares the complete group and every seat value before deleting them.

### Step 6: handle cleanup failure safely

If PostgreSQL committed successfully but Redis cleanup fails:

```text
PostgreSQL remains BOOKED
Redis may temporarily retain stale keys
API view gives permanent BOOKED priority
TTL later removes the stale keys
```

The successful permanent booking is not rolled back merely because temporary cleanup failed.

### Final state

```text
PostgreSQL A1 = BOOKED
PostgreSQL A2 = BOOKED
Redis lock keys = removed, or harmless until TTL expiry
```

## 12. Detailed release flow

Endpoint:

```http
POST /api/v1/seats/release
```

### Step 1: load the trusted group

The implementation reads the group key using `showId` and `lockId` and validates the stored owner against `userId`.

### Step 2: run compare-and-delete

The Lua script checks:

- the group value is unchanged;
- every seat value still contains the expected `lockId|userId`.

Only then does it delete all keys.

### Step 3: return available

No PostgreSQL update occurs. The permanent rows were already `AVAILABLE`.

```text
Before release: DB AVAILABLE + Redis key -> API LOCKED
After release:  DB AVAILABLE + no key    -> API AVAILABLE
```

## 13. Why compare-and-delete is necessary

Consider this timeline:

```text
10:00 User A locks A1 using LOCK-A
10:05 LOCK-A expires
10:06 User B locks A1 using LOCK-B
10:07 User A's delayed release reaches the server
```

This is unsafe:

```text
DELETE seat-lock:{show:101}:seat:A1
```

It would delete User B's current lock because the Redis key name is reused for the same seat.

The implemented release expects User A's value:

```text
LOCK-A|501
```

But Redis currently stores User B's value:

```text
LOCK-B|502
```

Because they differ, the Lua script returns `0` and deletes nothing.

The comparison and deletion are in one script because separate Java `GET` and `DELETE` calls would still leave a race window between the commands.

## 14. Detailed block flow and Redis coordination

An administrator may request that `A1` be blocked while a customer tries to lock it.

`blockSeats()`:

1. sorts the requested seat numbers;
2. pessimistically locks the PostgreSQL rows;
3. checks that permanent state is `AVAILABLE`;
4. calls `findLockedSeatNumbers()`;
5. rejects the block if Redis reports an active lock;
6. otherwise changes PostgreSQL to `BLOCKED`.

Customer locking and administrator blocking acquire the same database row locks in stable order. Therefore, only one operation can complete for the same seat:

```text
Customer wins -> Redis lock created; block sees it and fails
Admin wins    -> DB becomes BLOCKED; customer sees it and fails
```

## 15. Internal helper methods in `RedisSeatLockStore`

### `readOwnedGroup()`

This method:

1. reads the group key;
2. rejects an absent/expired lock;
3. decodes the value;
4. validates the show and lock identity;
5. validates the user owner;
6. returns both decoded details and the exact encoded value.

The exact encoded value is passed to Lua for comparison, ensuring the data did not change between Java's read and the script's atomic action.

### `encodeGroup()` and `decodeGroup()`

These methods translate between:

```text
SeatLockDetails Java record
```

and:

```text
LOCK-123|101|501|1783952700000|A1,A2
```

Malformed Redis data becomes `ExternalServiceException` instead of leaking parsing exceptions into the business layer.

### `keys()`

This method creates keys in the order expected by Lua:

```text
all individual seat keys first
group key last
```

The scripts rely on the last key being the group key.

### `redis()`

This helper executes a Redis operation and translates Spring `DataAccessException` into:

```text
ExternalServiceException("Redis seat lock store is unavailable")
```

This prevents Redis-specific infrastructure exceptions from spreading into the service layer.

## 16. Error behavior

| Situation | Result |
|---|---|
| Requested database seat does not exist | `ResourceNotFoundException` |
| A permanent seat is booked or blocked | `SeatConflictException` |
| A requested Redis seat key already exists | `SeatConflictException` |
| Lock ID is absent or expired | `ResourceNotFoundException` |
| Lock belongs to another user | `SeatConflictException` |
| Redis returns malformed data | `ExternalServiceException` |
| Redis cannot be reached | `ExternalServiceException` |
| Redis cleanup fails after DB booking | Booking remains successful; warning is logged |

The service fails closed when it cannot verify Redis. It does not pretend that a seat is available or that a lock succeeded.

## 17. Transaction boundaries

Redis and PostgreSQL are separate systems. A normal Spring `@Transactional` annotation does not make them one distributed transaction.

The confirmation boundary is deliberately:

```text
Verify and extend Redis lock
        |
        v
Open short PostgreSQL transaction
        |
        v
Recheck and book all seats
        |
        v
Commit PostgreSQL
        |
        v
Release Redis lock
```

`SeatBookingWriter` is a separate Spring bean because calling a separate bean allows Spring's transaction proxy to open a transaction around `confirm()`.

## 18. Production and test implementations

### Production

```text
SeatInventoryServiceImpl
    -> SeatLockStore
    -> RedisSeatLockStore
    -> shared Redis server
```

All service instances see the same temporary locks.

### Automated concurrency tests

```text
SeatInventoryServiceImpl
    -> SeatLockStore
    -> InMemorySeatLockStore
    -> synchronized Java maps
```

The in-memory implementation follows the same business contract so normal tests do not require a Redis installation. It is not suitable for production because application instances do not share Java memory and all locks disappear on restart.

There is also an optional real-Redis integration test for validating the actual Lua scripts when a Redis server is available.

## 19. How to debug the implementation

Place breakpoints in this order.

### Lock request

```text
SeatInventoryController.lockSeats()
SeatInventoryServiceImpl.lockSeats()
RedisSeatLockStore.acquire()
RedisSeatLockStore.keys()
RedisSeatLockStore.encodeGroup()
redisTemplate.execute()
```

Inspect:

- normalized seat order;
- generated lock ID;
- calculated `expiresAt`;
- Redis `KEYS` list;
- script arguments;
- script result.

### View request

```text
SeatInventoryServiceImpl.getSeats()
RedisSeatLockStore.findLockedSeatNumbers()
SeatInventoryServiceImpl.toEffectiveResponse()
```

Inspect the PostgreSQL status, Redis result, and final effective status separately.

### Confirm request

```text
SeatInventoryServiceImpl.confirmSeats()
RedisSeatLockStore.verifyAndExtend()
RedisSeatLockStore.readOwnedGroup()
SeatBookingWriter.confirm()
ShowSeat.book()
RedisSeatLockStore.release()
```

Observe that PostgreSQL changes to `BOOKED` inside `SeatBookingWriter`, not inside the Redis store.

### Release request

```text
SeatInventoryServiceImpl.releaseSeats()
RedisSeatLockStore.release()
RedisSeatLockStore.readOwnedGroup()
redisTemplate.execute(release script)
```

## 20. Manual learning scenario

Assume show `101` already has seats `A1` and `A2` in PostgreSQL.

### Lock

```http
POST /api/v1/seats/lock
Content-Type: application/json

{
  "showId": 101,
  "userId": 501,
  "seatNumbers": ["A1", "A2"]
}
```

Copy the returned lock ID.

### View

```http
GET /api/v1/seats/shows/101
```

`A1` and `A2` should appear as `LOCKED`, even though their database rows remain `AVAILABLE`.

### Competing user

Try locking `A1` as user `502`. The request should receive a conflict.

### Wrong-owner release

Try releasing the original lock as user `502`. It should receive a conflict and preserve user `501`'s lock.

### Correct confirmation

```http
POST /api/v1/seats/confirm
Content-Type: application/json

{
  "showId": 101,
  "userId": 501,
  "lockId": "COPY-THE-LOCK-ID"
}
```

PostgreSQL should now contain `BOOKED`, and the temporary Redis keys should be removed.

### Expiry alternative

Instead of confirming, wait five minutes and call GET again. The Redis keys should have expired, and the seats should appear `AVAILABLE` because PostgreSQL never left that permanent state.

## 21. Interview summary

> The service separates temporary and permanent state. PostgreSQL remains the durable source of truth for available, booked, and blocked seats, while Redis stores five-minute checkout leases. The business service depends on a `SeatLockStore` port, and `RedisSeatLockStore` is its production adapter. Individual seat keys support fast availability checks, while a group key stores the trusted selection and owner. Lua scripts make acquire, verify-and-extend, and compare-and-delete operations atomic across multiple seats. Confirmation revalidates the Redis owner, briefly extends the TTL, commits the booking in a short PostgreSQL transaction, and then safely removes the Redis keys.

## 22. Memory aid

```text
LOCK:
DB validate -> Redis check-and-create -> return expiry

VIEW:
DB permanent state + Redis temporary key -> effective status

CONFIRM:
Redis verify-and-extend -> DB book -> Redis release

RELEASE:
Redis compare-and-delete -> DB remains AVAILABLE

EXPIRY:
Redis TTL removes key -> DB was already AVAILABLE
```

