# Running, testing, and API examples

## Required local components

- PostgreSQL with a database named `booking_db`;
- Eureka on port `8761`;
- Show Service registered as `show-service`;
- Seat Inventory Service registered as `seat-inventory-service`;
- Redis required by Seat Inventory Service;
- Booking Service on port `8085`;
- API Gateway on port `8088`.

Flyway creates `bookings` and `booking_seats`. Hibernate uses `validate`, so it
checks the migration instead of changing production schema automatically.

## Start Booking Service

From the repository root, using any existing project Maven wrapper:

```powershell
api-gateway\mvnw.cmd -f booking-service\pom.xml spring-boot:run
```

Environment variables can override configuration:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/booking_db"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "root"
$env:EUREKA_SERVER_URL = "http://localhost:8761/eureka/"
```

## Create a booking through the Gateway

```http
POST http://localhost:8088/api/v1/bookings
Content-Type: application/json
Idempotency-Key: checkout-user10-show20-attempt1
X-Correlation-ID: checkout-demo-1

{
  "userId": 10,
  "showId": 20,
  "seatNumbers": ["A1", "A2"]
}
```

First successful response:

```http
HTTP/1.1 201 Created
Location: /api/v1/bookings/{bookingId}
Idempotency-Replayed: false
```

Send the identical request and key again. The response is `200 OK`, contains the
same booking ID, and includes `Idempotency-Replayed: true`.

## Retrieve a booking

```http
GET http://localhost:8088/api/v1/bookings/{bookingId}
```

## Expected failures to practise

| Test | Expected result |
|---|---|
| Missing `Idempotency-Key` | `400` |
| Repeated seat number | `400 DUPLICATE_SEATS` |
| Cancelled or started show | `409` |
| Seat already locked/booked | `409 SEATS_UNAVAILABLE` |
| Same key with changed seats | `409 IDEMPOTENCY_KEY_REUSED` |
| Booking Service stopped | Gateway-controlled `503` |
| Show or Inventory unavailable | Booking-controlled `503` |

## Automated tests

```powershell
api-gateway\mvnw.cmd -f booking-service\pom.xml test
api-gateway\mvnw.cmd test -f api-gateway\pom.xml
```

The tests cover domain orchestration, idempotent replay, duplicate seats,
compensating release, Flyway/JPA mapping, Gateway routing, rate-limit properties,
circuit opening, and controlled outage responses.

## Known next phases

1. Payment creation and webhook handling.
2. Confirm seats and move `PENDING` to `CONFIRMED`.
3. Cancel and release seats safely.
4. Expire unpaid bookings with a scheduled worker.
5. Authenticate requests and take `userId` from the token instead of the body.
6. Add a durable outbox and reconciliation for cross-service failures.
