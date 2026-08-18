# Why Seat Locks Moved to Redis: Previous Problems and Solutions

## 1. Previous design

Previously, a temporary customer lock was stored in the `show_seats` PostgreSQL row:

```text
status = LOCKED
lock_id = LOCK-123
locked_by_user_id = 501
```

That implementation could prevent two database transactions from locking the same seat, but the five-minute business requirement was incomplete. A database value does not disappear just because five minutes have passed.

## 2. Problems in the previous design

### Problem: abandoned locks could remain forever

If the customer closed the browser, lost internet, or never called `/release`, the row stayed `LOCKED`. The seat could become permanently unavailable even though nobody paid for it.

Possible database-only fixes would require an `expires_at` column plus cleanup logic, scheduled jobs, or expiry checks in every relevant query and write path. Missing one check would produce stale behavior.

### Problem: temporary and permanent state were mixed

`BOOKED` and `BLOCKED` are durable business states. `LOCKED` is a short-lived checkout lease. Keeping all three in the same database status column made a temporary reservation look like permanent inventory state.

That increased the number of transitions and cleanup cases that every database operation had to understand.

### Problem: expiry races are difficult

Suppose an old lock expires and User B receives a new lock for the same seat. A delayed release request from User A must not clear User B's lock.

A simple `DELETE key` or unconditional database update is unsafe. Cleanup must verify ownership at the same instant that it deletes the lock.

### Problem: locking several seats could partially succeed

For `A1`, `A2`, and `A3`, checking and writing each temporary key separately can produce this bad result:

```text
A1 locked successfully
A2 locked successfully
A3 was already locked
```

The customer requested one selection, so partial acquisition is incorrect. Check-all and write-all must be one indivisible operation.

### Problem: a normal database transaction cannot cover Redis safely

PostgreSQL and Redis are separate systems. Spring's local `@Transactional` database transaction cannot atomically commit or roll back Redis with PostgreSQL.

Pretending that one annotation provides a distributed transaction would leave failure windows hidden. The service instead gives each system a clear role and defines what happens at every boundary.

### Problem: database writes for short-lived browsing activity

Seat selection is high-churn activity: users select, release, change their minds, or time out. Updating durable rows for each temporary action adds database writes and leaves cleanup work behind.

Redis is designed for fast expiring data, so the database is reserved for permanent inventory changes.

## 3. Solution mapping

| Previous problem | Implemented solution | Result |
|---|---|---|
| Abandoned lock stays forever | Redis TTL on every lock key | Automatic release after five minutes |
| Temporary and permanent states mixed | PostgreSQL stores permanent state; Redis stores temporary locks | Clear data ownership |
| Old request deletes a new lock | Lua compares expected owner/value before deleting | Safe ownership-aware release |
| Multi-seat partial success | One Lua check-and-set script for all keys | All seats lock or none lock |
| Confirm runs as lock expires | Verify-and-extend Lua script | Short protected confirmation window |
| Two users race for one seat | Atomic Redis script plus PostgreSQL permanent-state checks | Only one lock wins |
| Admin block races with customer lock | Both paths coordinate through sorted pessimistic database row locks and Redis check | Only block or lock wins |
| Redis cleanup fails after booking | PostgreSQL `BOOKED` wins; Redis keys expire | No lost booking and no double booking |
| Redis unavailable | Fail closed with external-service error | No false availability or fake lock success |
| Redis code spreads everywhere | `SeatLockStore` port and Redis adapter | Business layer is testable and storage can be replaced |

## 4. Before and after examples

### Customer abandons checkout

Before:

```text
PostgreSQL row becomes LOCKED
Customer closes browser
No release call arrives
Row can remain LOCKED indefinitely
```

After:

```text
PostgreSQL remains AVAILABLE
Redis key represents LOCKED for five minutes
Customer closes browser
Redis TTL deletes the key
Seat automatically appears AVAILABLE again
```

### Two users lock the same seats

Before an atomic temporary lock operation:

```text
User A checks A1: free
User B checks A1: free
Both attempt to claim A1
Correctness depends on later database contention handling
```

After:

```text
User A's Lua script checks and creates A1 atomically
User B's Lua script sees the existing key and returns conflict
Exactly one user receives the lock
```

### Old release arrives late

Unsafe approach:

```text
User A lock expires
User B locks A1
User A's delayed request blindly deletes A1
User B loses a valid lock
```

Implemented approach:

```text
User A lock expires
User B locks A1 with a different value
User A's release Lua script compares ownership
Values do not match, so nothing is deleted
```

### Booking confirmation succeeds but Redis cleanup fails

Implemented outcome:

```text
Redis ownership verified and TTL extended
PostgreSQL changes A1 to BOOKED and commits
Redis deletion fails temporarily
API still treats PostgreSQL BOOKED as permanent truth
Remaining Redis key expires by TTL
```

Rolling the database booking back at this point would be worse: payment/order processing may already consider the confirmation successful. The safe rule is that a committed permanent booking wins over temporary cleanup.

## 5. Why PostgreSQL row locking still exists

Redis replaces the storage of temporary locks; it does not remove every use of database locks.

Pessimistic database row locks are still needed while changing permanent state and while coordinating the admin block operation with customer locking. They protect transitions such as:

```text
AVAILABLE -> BOOKED
AVAILABLE -> BLOCKED
BLOCKED   -> AVAILABLE
```

Redis protects temporary ownership:

```text
temporarily free -> temporarily locked -> expired/released
```

The two mechanisms solve different concurrency problems.

## 6. Migration behavior

Flyway migration `V2__release_legacy_database_locks.sql` changes existing legacy `LOCKED` rows back to `AVAILABLE`, removes the old lock index, and tightens the database constraint so only permanent states can be stored. The legacy nullable ownership columns remain physically present for a safe cross-database migration, but the entity no longer maps or writes them; the existing lock-data constraint keeps them null for all allowed states.

Why release them instead of copying them to Redis?

- the old rows do not contain a trustworthy five-minute expiry time;
- after a restart, their actual remaining lifetime cannot be calculated;
- treating an unknown old lock as active forever repeats the original problem.

`BOOKED` and `BLOCKED` rows are not changed by the migration.

## 7. Deliberate consistency model

This is not a distributed ACID transaction across PostgreSQL and Redis. It is an explicitly ordered workflow:

1. Redis proves that the caller owns a live temporary lock.
2. Redis extends that lock briefly.
3. A short PostgreSQL transaction revalidates and commits the permanent booking.
4. Redis cleanup runs after the commit.

The database recheck is important. Redis ownership alone is not permission to overwrite a seat that somehow became `BOOKED` or `BLOCKED`. This second validation is defense in depth and prevents double booking.

## 8. Trade-offs to explain in an interview

- Redis is now required for viewing and creating temporary locks. This is intentional fail-closed behavior for correctness.
- Redis TTL provides automatic expiry, but TTL is a lease, not a payment guarantee. Confirm must still validate ownership and permanent database state.
- Lua adds small script complexity, but it gives atomic multi-key operations that separate GET/SET calls cannot provide.
- PostgreSQL remains the durable source of truth, so loss of Redis can lose temporary selections but cannot lose confirmed bookings.
- The port/adapter boundary makes Redis replaceable and keeps business tests independent from Redis infrastructure.

The core design sentence is:

> PostgreSQL owns permanent seat truth; Redis owns short-lived checkout leases; Lua makes each multi-seat lease operation atomic.
