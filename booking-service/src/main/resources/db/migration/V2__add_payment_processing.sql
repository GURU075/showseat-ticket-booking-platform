ALTER TABLE bookings ADD COLUMN payment_id UUID;
ALTER TABLE bookings ADD CONSTRAINT uk_booking_payment_id UNIQUE (payment_id);
ALTER TABLE bookings ADD CONSTRAINT ck_booking_confirmed_payment
    CHECK (status <> 'CONFIRMED' OR payment_id IS NOT NULL);

CREATE TABLE processed_payment_events (
    event_id UUID PRIMARY KEY,
    payment_id UUID NOT NULL,
    booking_id UUID NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
