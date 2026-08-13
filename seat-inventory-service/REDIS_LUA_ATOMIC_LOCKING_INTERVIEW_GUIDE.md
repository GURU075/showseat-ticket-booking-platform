# Redis Lua Atomic Locking — Interview Guide

## 1. Why this topic matters

Seat locking is a concurrency problem. Many customers can attempt to lock the same seats at nearly the same time, and a multi-seat selection must behave as one operation.

The most important interview statement is:

> We use Redis Lua scripts because checking a lock and changing it must happen atomically. Separate Redis commands leave a race-condition window.

In this service, Lua is used for three operations:

1. acquire all selected seats;
2. verify ownership and extend the lock before confirmation;
3. verify ownership and release the lock safely.

The scripts are stored under `src/main/resources/redis` because they are application resources loaded from the Java classpath at runtime.

## 2. What atomic means

Atomic means other Redis commands cannot run in the middle of the operation.

Suppose Java performs separate commands:

```text
Check A1
Check A2
Create A1
Create A2
```

A second customer's commands can run between those steps:

```text
User A checks A1: free
User B checks A1: free
User A creates A1
User B also attempts to create A1
```

A Lua script sends the complete decision to Redis:

```text
Check A1 and A2; if both are free, create both; otherwise create neither.
```

Redis runs that script as one indivisible operation.

## 3. How Java sends data to a Lua script

A Redis script receives two kinds of input:

### `KEYS`

`KEYS` contains Redis key names that the script will access.

For locking `A1` and `A2`:

```text
KEYS[1] = seat-lock:{show:101}:seat:A1
KEYS[2] = seat-lock:{show:101}:seat:A2
KEYS[3] = seat-lock:{show:101}:group:LOCK-123
```

Lua list indexes begin at `1`, not `0`.

### `ARGV`

`ARGV` contains ordinary values and configuration arguments.

For acquisition:

```text
ARGV[1] = LOCK-123|501
ARGV[2] = encoded group metadata
ARGV[3] = 300000
```

`300000` milliseconds is five minutes.

Key names should be passed through `KEYS`; values should be passed through `ARGV`. This makes the accessed keys explicit and is especially important for Redis Cluster.

## 4. The two kinds of Redis keys

### Seat key

One key exists for each temporarily locked seat:

```text
seat-lock:{show:101}:seat:A1
```

Its value contains the lock and owner:

```text
LOCK-123|501
```

This key answers:

> Is this particular seat temporarily locked, and by which lock?

### Group key

One group key represents the whole customer selection:

```text
seat-lock:{show:101}:group:LOCK-123
```

It contains the lock ID, show ID, user ID, expiry information, and all seat numbers.

This key answers:

> Which seats belong to this lock ID, and who owns the selection?

The group key is necessary because confirm/release requests contain a `lockId`, not the complete trusted seat list. The server reconstructs the selection from its own stored lock metadata instead of trusting seats supplied by the client.

## 5. Why the keys contain `{show:101}`

The braces define a Redis Cluster hash tag:

```text
seat-lock:{show:101}:seat:A1
seat-lock:{show:101}:seat:A2
seat-lock:{show:101}:group:LOCK-123
```

Redis Cluster places keys with the same text inside `{...}` in the same hash slot. Multi-key Lua scripts require their keys to be available together. Therefore, every lock key for one show uses the same `{show:101}` tag.

Interview answer:

> The hash tag makes the key design cluster-compatible by colocating all keys used by one multi-key script.

## 6. Script 1: acquire-seat-locks.lua

Purpose:

> Lock every selected seat, or lock none of them.

### Step 1: find the group key position

```lua
local groupKeyIndex = #KEYS
```

`#KEYS` is the number of items in `KEYS`. The Java code always puts the group key last.

For two seats:

```text
groupKeyIndex = 3
```

### Step 2: verify that no key exists

```lua
for index = 1, groupKeyIndex do
    if redis.call('EXISTS', KEYS[index]) == 1 then
        return 0
    end
end
```

If any seat is already locked, the script immediately returns `0`. Because no writes happened yet, there is no partial lock.

### Step 3: create each seat key with TTL

```lua
for index = 1, groupKeyIndex - 1 do
    redis.call('SET', KEYS[index], ARGV[1], 'PX', ARGV[3])
end
```

`PX` tells Redis that the TTL is in milliseconds.

### Step 4: create the group key with the same TTL

```lua
redis.call('SET', KEYS[groupKeyIndex], ARGV[2], 'PX', ARGV[3])
return 1
```

Return values:

```text
1 = every key was created
0 = at least one key already existed
```

### Scenario

User A requests `A1` and `A2`, but User B already owns `A2`:

```text
Check A1 -> absent
Check A2 -> present
Return 0
A1 is not created
```

This is all-or-nothing multi-seat locking.

## 7. Script 2: verify-and-extend-seat-locks.lua

Purpose:

> Ensure that the complete lock still belongs to this request, then extend it briefly while booking is confirmed.

### Step 1: compare the group value

```lua
if redis.call('GET', KEYS[groupKeyIndex]) ~= ARGV[2] then
    return 0
end
```

`~=` means “not equal.” A missing key also fails because `GET` does not equal the expected value.

### Step 2: compare every seat value

```lua
for index = 1, groupKeyIndex - 1 do
    if redis.call('GET', KEYS[index]) ~= ARGV[1] then
        return 0
    end
end
```

Every seat must still belong to the expected lock ID and user. If one key expired, disappeared, or changed, confirmation is rejected.

### Step 3: extend every TTL

```lua
for index = 1, groupKeyIndex do
    redis.call('PEXPIRE', KEYS[index], ARGV[3])
end

return 1
```

The service uses a one-minute confirmation TTL. This does not make the booking permanent. It only provides a short window for the PostgreSQL booking transaction to complete.

### Why verify and extend are in one script

This is unsafe:

```text
GET and verify lock
lock expires here
PEXPIRE lock
```

The key could expire between separate commands. The script makes verification and extension one atomic decision.

## 8. Script 3: release-seat-locks.lua

Purpose:

> Delete the selection only if every key still belongs to the expected lock and user.

The script first compares the group and every seat value. Only after all comparisons succeed does it run:

```lua
redis.call('DEL', unpack(KEYS))
return 1
```

`unpack(KEYS)` passes every list item as an individual argument to `DEL`.

Conceptually:

```text
DEL seat-A1 seat-A2 group-LOCK-123
```

### The expiry race this prevents

```text
12:00 User A locks A1 using LOCK-A
12:05 LOCK-A expires
12:06 User B locks A1 using LOCK-B
12:07 User A's delayed release request arrives
```

Blind deletion would remove User B's valid lock. Compare-and-delete sees:

```text
Expected by User A: LOCK-A|501
Current Redis value: LOCK-B|502
```

The values differ, so the script returns `0` and deletes nothing.

## 9. Why Java `GET` followed by `DELETE` is still unsafe

This code looks reasonable but has a race condition:

```java
String current = redis.get(key);
if (expected.equals(current)) {
    redis.delete(key);
}
```

Possible execution:

```text
GET reads User A's old value
User A's key expires
User B creates a new value under the same key
DELETE removes User B's value
```

The ownership check is useful only if nothing can change between the comparison and deletion. Lua provides that atomicity.

## 10. How Spring loads and executes the scripts

`SeatLockRedisConfig` loads each file once as a `RedisScript<Long>`:

```java
RedisScript.of(
    new ClassPathResource("redis/acquire-seat-locks.lua"),
    Long.class
);
```

Why `ClassPathResource` works:

```text
src/main/resources/redis/*.lua
        -> copied into the built application classpath
        -> loaded as redis/*.lua
```

Why `Long.class` is supplied:

```text
Lua returns 1 or 0
Spring converts that result to Java Long
```

`RedisSeatLockStore` executes a script with `StringRedisTemplate`:

```java
redisTemplate.execute(script, keys, arguments...);
```

The flow mapping is:

```text
POST /lock
  -> acquire-seat-locks.lua

POST /confirm
  -> verify-and-extend-seat-locks.lua
  -> PostgreSQL transaction changes AVAILABLE to BOOKED
  -> release-seat-locks.lua

POST /release
  -> release-seat-locks.lua
```

## 11. Why PostgreSQL is still required

Redis holds only a temporary checkout lease. It is not the permanent booking record.

```text
Redis      -> temporary LOCKED state with expiry
PostgreSQL -> permanent AVAILABLE, BOOKED, and BLOCKED states
```

During confirmation, the service rechecks PostgreSQL and changes all selected seats to `BOOKED` inside a short database transaction. This prevents Redis ownership from overwriting a permanent conflict.

If Redis cleanup fails after the database commit:

- PostgreSQL remains `BOOKED`;
- the stale Redis key cannot override the permanent booked state;
- the Redis TTL eventually removes it.

## 12. Important scenarios to explain in an interview

### Two users request the same seat

The first Lua script to create the key succeeds. The second sees the existing key and returns a conflict.

### One seat in a selection is already locked

The acquisition script creates none of the requested keys. This prevents partial checkout selections.

### A customer abandons checkout

Redis automatically expires all selection keys after five minutes. No release request or database cleanup scheduler is required.

### Another user tries to confirm the lock

The stored owner does not match the request user, so verification fails and PostgreSQL is not changed.

### An old release request arrives after a new owner locks the seat

The expected value does not match the current value. Compare-and-delete returns failure and preserves the new lock.

### A lock is about to expire during confirmation

The verification script atomically validates and extends all keys for one minute before the short database transaction starts.

### Redis is unavailable

The service fails closed. It does not report a successful lock or incorrectly claim that an unchecked seat is available.

## 13. Common interview questions and answers

### Why Redis instead of storing `LOCKED` only in PostgreSQL?

Redis provides native TTL for short-lived locks. Abandoned selections expire automatically, while PostgreSQL remains responsible for durable bookings and blocks.

### Why Lua instead of normal Redis commands?

The operation requires multiple checks and writes to behave atomically. Separate commands create race windows; one Lua script is executed as a single Redis operation.

### Why not simply use `SETNX`?

`SET NX` works well for one key, but a customer can select several seats. Calling it separately can lock some seats and fail on another. The Lua script provides all-or-nothing acquisition across the selection.

### Why store both seat keys and a group key?

Seat keys provide fast availability checks. The group key maps one lock ID to its trusted owner and complete seat selection for confirm/release.

### Why include `lockId` and `userId` in the value?

They establish ownership. Release and confirm compare the expected ownership with the current value, preventing another user or a delayed old request from modifying a newer lock.

### Does `@Transactional` make PostgreSQL and Redis one transaction?

No. A normal Spring database transaction does not create a distributed transaction with Redis. The service uses an explicitly ordered workflow, short database transactions, revalidation, ownership-aware cleanup, and TTL-based recovery.

### Is Redis the source of truth?

Redis is the source of truth only for temporary lock ownership. PostgreSQL is the durable source of truth for permanent seat status.

### What is the main trade-off?

Lua provides correctness and fewer network round trips, but scripts must remain short and carefully tested because Redis processes the script atomically and other commands wait until it finishes.

## 14. A strong interview answer

> In the Seat Inventory Service, PostgreSQL stores permanent states such as available, booked, and blocked, while Redis stores five-minute checkout locks. Each seat has a Redis key, and the whole selection has a group key. I used Lua scripts because a multi-seat check-and-set must be atomic: either every requested seat is locked or none is. Confirmation atomically verifies ownership and extends the TTL before a short PostgreSQL booking transaction. Release uses compare-and-delete, so a delayed request from an expired lock cannot delete a newer user's lock. The keys use a show-based hash tag so the multi-key scripts are compatible with Redis Cluster.

## 15. One-line memory aid

```text
Acquire = check and create
Confirm = check and extend, then book
Release = check and delete
```

