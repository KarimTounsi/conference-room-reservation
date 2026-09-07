CREATE TABLE conference_room
(
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    max_occupancy INTEGER      NOT NULL,
    CONSTRAINT uq_conference_room_name UNIQUE (name),
    CONSTRAINT chk_conference_room_max_occupancy CHECK (max_occupancy > 0)
);

CREATE TABLE reservation
(
    id         BIGSERIAL PRIMARY KEY,
    room_id    BIGINT       NOT NULL REFERENCES conference_room (id),
    booked_by  VARCHAR(200) NOT NULL,
    start_time TIMESTAMPTZ  NOT NULL,
    end_time   TIMESTAMPTZ  NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    version    BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_reservation_time_range CHECK (end_time > start_time),
    -- Without this CHECK the partial exclusion constraint added in V2 could be bypassed by
    -- writing a misspelled status directly to the database.
    CONSTRAINT chk_reservation_status CHECK (status IN ('ACTIVE', 'CANCELLED'))
);

-- Covers listing a room's reservations ordered by start, filtering by date range, and the
-- overlap probe (room_id = ? AND start_time < ? AND end_time > ?).
CREATE INDEX idx_reservation_room_start ON reservation (room_id, start_time, end_time);
