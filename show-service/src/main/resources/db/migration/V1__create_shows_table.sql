CREATE TABLE shows (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL,
    venue_id BIGINT NOT NULL,
    screen_id BIGINT NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    base_price NUMERIC(10, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_screen_start_time UNIQUE (screen_id, start_time),
    CONSTRAINT ck_show_time_range CHECK (start_time < end_time),
    CONSTRAINT ck_show_base_price_positive CHECK (base_price > 0),
    CONSTRAINT ck_show_status CHECK (status IN ('SCHEDULED', 'CANCELLED', 'COMPLETED'))
);

CREATE INDEX idx_shows_event_start ON shows (event_id, start_time);
CREATE INDEX idx_shows_screen_start ON shows (screen_id, start_time);
