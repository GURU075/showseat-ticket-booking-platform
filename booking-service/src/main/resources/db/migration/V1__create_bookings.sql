CREATE TABLE bookings (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL,
    show_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    total_amount NUMERIC(12, 2),
    currency VARCHAR(3),
    lock_id VARCHAR(80),
    lock_expires_at TIMESTAMP WITH TIME ZONE,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_booking_user_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT uk_booking_lock_id UNIQUE (lock_id),
    CONSTRAINT ck_booking_status CHECK (
        status IN ('CREATING', 'PENDING', 'CONFIRMED', 'CANCELLED', 'EXPIRED')
    ),
    CONSTRAINT ck_booking_pending_data CHECK (
        status = 'CREATING'
        OR (total_amount IS NOT NULL AND currency IS NOT NULL
            AND lock_id IS NOT NULL AND lock_expires_at IS NOT NULL)
    )
);

CREATE TABLE booking_seats (
    booking_id UUID NOT NULL,
    seat_order INTEGER NOT NULL,
    seat_number VARCHAR(20) NOT NULL,
    PRIMARY KEY (booking_id, seat_order),
    CONSTRAINT fk_booking_seats_booking
        FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE,
    CONSTRAINT uk_booking_seat UNIQUE (booking_id, seat_number)
);

CREATE INDEX idx_bookings_user_created ON bookings (user_id, created_at DESC);
CREATE INDEX idx_bookings_show_status ON bookings (show_id, status);
