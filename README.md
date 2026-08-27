# ShowSeat Ticket Booking Platform

A BookMyShow-style event ticket booking platform built using Spring Boot microservices.

## Services

- event-service
- venue-service
- show-service
- seat-inventory-service
- [booking-service](booking-service/docs/README.md) — idempotent booking creation and seat-lock orchestration
- [payment-service](payment-service/docs/README.md) — idempotent payments with a Kafka transactional outbox
- notification-service
- discovery-server
- api-gateway
- auth-service
- user-service
