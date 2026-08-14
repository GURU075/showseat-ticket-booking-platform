# Seat Inventory Service — Documentation Index

This file organizes the learning documents without moving or renaming them, so existing links and Git history remain safe.

## Recommended learning order

### 1. Learn the service fundamentals

[SEAT_INVENTORY_SERVICE_GUIDE.md](SEAT_INVENTORY_SERVICE_GUIDE.md)

Start here. It covers the service responsibility, seat states, APIs, validation, transactions, concurrency, and the original database-backed implementation.

### 2. Learn inventory creation and communication with other services

[FEIGN_CLIENT_AND_INVENTORY_CREATION_GUIDE.md](FEIGN_CLIENT_AND_INVENTORY_CREATION_GUIDE.md)

Read this to understand how Seat Inventory validates show, venue, screen, and physical-seat data through Show Service and Venue Service.

### 3. Understand why there are two client layers

[WHY_TWO_CLIENT_LAYERS_SIMPLE_GUIDE.md](WHY_TWO_CLIENT_LAYERS_SIMPLE_GUIDE.md)

This explains the difference between Feign HTTP interfaces and the business-facing `InventoryCatalogClient`, including adapter pattern and dependency inversion.

### 4. Understand why temporary locks moved to Redis

[REDIS_MIGRATION_PROBLEMS_AND_SOLUTIONS.md](REDIS_MIGRATION_PROBLEMS_AND_SOLUTIONS.md)

Read this before studying the Redis code. It explains what was wrong with permanent database lock rows, the expiry problem, race conditions, and the before/after design.

### 5. Learn the complete Redis seat-lock flow

[REDIS_SEAT_LOCK_FEATURES_AND_FLOW.md](REDIS_SEAT_LOCK_FEATURES_AND_FLOW.md)

This covers Redis/PostgreSQL responsibilities, key formats, TTL, lock/confirm/release flows, configuration, failure behavior, and testing.

### 6. Trace the Redis implementation in detail

[REDIS_IMPLEMENTATION_DETAILED_FLOW.md](REDIS_IMPLEMENTATION_DETAILED_FLOW.md)

Use this as the code walkthrough. It traces configuration, storage format, every API flow, helper methods, transaction boundaries, errors, debugging breakpoints, and a manual test scenario.

### 7. Study Lua atomic locking for interviews

[REDIS_LUA_ATOMIC_LOCKING_INTERVIEW_GUIDE.md](REDIS_LUA_ATOMIC_LOCKING_INTERVIEW_GUIDE.md)

This is the deep-dive interview document. It explains every Lua resource file, `KEYS`, `ARGV`, atomicity, compare-and-delete, expiry races, Redis Cluster hash tags, scenarios, and interview questions.

## Documentation grouped by topic

| Topic | Document | Main question answered |
|---|---|---|
| Service fundamentals | `SEAT_INVENTORY_SERVICE_GUIDE.md` | What does this microservice do? |
| Feign and creation | `FEIGN_CLIENT_AND_INVENTORY_CREATION_GUIDE.md` | How is inventory created using remote catalog data? |
| Architecture patterns | `WHY_TWO_CLIENT_LAYERS_SIMPLE_GUIDE.md` | Why separate business ports from Feign transport clients? |
| Redis motivation | `REDIS_MIGRATION_PROBLEMS_AND_SOLUTIONS.md` | What problems did database-only locks cause? |
| Redis implementation | `REDIS_SEAT_LOCK_FEATURES_AND_FLOW.md` | How does the Redis-backed feature work end to end? |
| Detailed code flow | `REDIS_IMPLEMENTATION_DETAILED_FLOW.md` | How does each class and method participate in the implementation? |
| Lua and interviews | `REDIS_LUA_ATOMIC_LOCKING_INTERVIEW_GUIDE.md` | Why and how do atomic Redis scripts prevent races? |
| Generated project help | `HELP.md` | Which framework reference links were generated with the project? |

## Suggested code-reading order

After reading the documents, debug the implementation in this order:

```text
SeatInventoryController
        ↓
SeatInventoryServiceImpl
        ↓
SeatLockStore
        ↓
RedisSeatLockStore
        ↓
acquire-seat-locks.lua
verify-and-extend-seat-locks.lua
release-seat-locks.lua
        ↓
SeatBookingWriter
        ↓
ShowSeatRepository and ShowSeat
```

## Quick interview revision path

If you have limited preparation time, read these sections in order:

1. `REDIS_MIGRATION_PROBLEMS_AND_SOLUTIONS.md` — Problems and solution mapping.
2. `REDIS_SEAT_LOCK_FEATURES_AND_FLOW.md` — Request flows.
3. `REDIS_IMPLEMENTATION_DETAILED_FLOW.md` — Complete code walkthrough.
4. `REDIS_LUA_ATOMIC_LOCKING_INTERVIEW_GUIDE.md` — Strong interview answer and common questions.

## Core architecture summary

```text
PostgreSQL = permanent seat truth
Redis      = temporary five-minute ownership
Lua        = atomic multi-seat operations
TTL        = automatic abandoned-lock cleanup
lockId     = identifies one selection
userId     = proves ownership
```
