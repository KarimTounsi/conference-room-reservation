CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Database-level guarantee: two ACTIVE reservations of the same room can never hold overlapping
-- half-open [start, end) intervals. This closes the race window between the application's overlap
-- check and the insert, and it holds for writes that bypass the application entirely.
ALTER TABLE reservation
    ADD CONSTRAINT excl_reservation_active_overlap
        EXCLUDE USING gist (
            room_id WITH =,
            tstzrange(start_time, end_time, '[)') WITH &&
            ) WHERE (status = 'ACTIVE');
