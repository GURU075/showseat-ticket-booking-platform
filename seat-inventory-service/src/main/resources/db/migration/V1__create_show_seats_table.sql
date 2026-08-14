CREATE TABLE show_seats (
    id BIGSERIAL PRIMARY KEY,
    show_id BIGINT NOT NULL,
    seat_number VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    lock_id VARCHAR(50),
    locked_by_user_id BIGINT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_show_seat_number UNIQUE (show_id, seat_number),
    CONSTRAINT ck_show_seat_status
        CHECK (status IN ('AVAILABLE', 'LOCKED', 'BOOKED', 'BLOCKED')),
    CONSTRAINT ck_show_seat_lock_data
        CHECK (
            (status = 'LOCKED' AND lock_id IS NOT NULL AND locked_by_user_id IS NOT NULL)
            OR
            (status <> 'LOCKED' AND lock_id IS NULL AND locked_by_user_id IS NULL)
        )
);

CREATE INDEX idx_show_seats_show_status
    ON show_seats (show_id, status, seat_number);

CREATE INDEX idx_show_seats_lock
    ON show_seats (show_id, lock_id);
