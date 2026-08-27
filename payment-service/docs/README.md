# Payment Service

If you are learning this service for the first time, start with
[the simple payment guide](SIMPLE_PAYMENT_GUIDE.md). It separates the basic payment flow from the
Kafka and transaction details.

Payment Service owns payment attempts and provider results. Booking Service owns the booking
lifecycle and is the only service that creates payments, so clients cannot choose their own
amount.

## Flow

1. The client calls `POST /api/v1/bookings/{bookingId}/payments` through the Gateway.
2. Booking Service sends the booking ID, user ID, total, and currency to Payment Service.
3. Payment Service creates one idempotent `PENDING` payment for the booking.
4. A provider callback changes the payment to `SUCCEEDED` or `FAILED`.
5. The same database transaction inserts a versioned event into `payment_outbox`.
6. The outbox publisher sends the JSON event to `payment-events.v1`.
7. Booking Service confirms or releases the seats and records the event ID for deduplication.

Kafka delivery is intentionally at-least-once. Seat confirmation stores the original lock ID in
PostgreSQL, making a repeated confirmation safe even after Redis has removed the temporary lock.

## Local dependencies

- PostgreSQL database `payment_db`
- Kafka at `localhost:9092`
- Eureka at `localhost:8761`
- Booking Service, Seat Inventory Service, and Redis

Payment Service runs on port `8086`. Configuration can be overridden with `DB_URL`,
`KAFKA_BOOTSTRAP_SERVERS`, `EUREKA_SERVER_URL`, and `PAYMENT_EVENTS_TOPIC`.

## Exercise the flow

Create payment through the Gateway:

```http
POST http://localhost:8088/api/v1/bookings/{bookingId}/payments
Idempotency-Key: pay-booking-attempt-1
```

Copy `providerReference` from the response, then simulate a provider callback directly to the
internal Payment Service endpoint:

```http
POST http://localhost:8086/internal/v1/payment-provider/events
Content-Type: application/json

{
  "providerEventId": "provider-demo-1",
  "providerReference": "sim_REPLACE_ME",
  "status": "SUCCEEDED",
  "failureReason": null
}
```

Use `FAILED` with a non-empty `failureReason` to test compensation. Repeating the same provider
event or Kafka message is safe.

For a shorter local-only simulation, send `{"status":"SUCCEEDED"}` to the relative
`checkoutUrl` returned by payment creation.

The internal callback is a development adapter, not a production payment-provider integration.
A real adapter must verify the provider signature before calling the payment application service.
