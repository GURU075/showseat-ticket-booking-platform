UPDATE show_seats
SET status = 'AVAILABLE',
    lock_id = NULL,
    locked_by_user_id = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE status = 'LOCKED';

DROP INDEX IF EXISTS idx_show_seats_lock;

ALTER TABLE show_seats DROP CONSTRAINT ck_show_seat_status;

ALTER TABLE show_seats
    ADD CONSTRAINT ck_show_seat_status
        CHECK (status IN ('AVAILABLE', 'BOOKED', 'BLOCKED'));
