ALTER TABLE show_seats ADD COLUMN confirmed_lock_id VARCHAR(80);
CREATE INDEX idx_show_seats_confirmed_lock ON show_seats (show_id, confirmed_lock_id);
ALTER TABLE show_seats ADD CONSTRAINT ck_show_seat_confirmation
    CHECK (status = 'BOOKED' OR confirmed_lock_id IS NULL);
