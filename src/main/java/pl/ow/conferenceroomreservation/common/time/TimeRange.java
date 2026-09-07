package pl.ow.conferenceroomreservation.common.time;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * A half-open time interval {@code [start, end)}.
 *
 * <p>Half-open is what makes back-to-back bookings legal: a range ending at 10:00 and one starting
 * at 10:00 do not overlap, because the end instant belongs to the next booking.
 *
 * <p>All comparisons use {@link OffsetDateTime#isBefore} / {@link OffsetDateTime#isAfter}, which
 * compare instants. {@code equals} would also compare the offset, so {@code 09:00+02:00} and
 * {@code 07:00Z} - the same moment - would wrongly look different.
 *
 * <p>Both ends are truncated to microseconds, the precision PostgreSQL {@code TIMESTAMPTZ} stores.
 * Modelling the range more finely than it can be persisted breaks the value object's own promise
 * twice over: a caller sending {@code 09:00:00.0000001Z} to {@code 09:00:00.0000002Z} would pass
 * the emptiness check here and then violate {@code chk_reservation_time_range} in the database,
 * and a range that does survive would be echoed back at a precision no later read can reproduce.
 * Truncating - rather than rounding, as the server does - never moves an instant forward into time
 * the caller did not ask for.
 */
public record TimeRange(OffsetDateTime start, OffsetDateTime end) {

    public TimeRange {
        Objects.requireNonNull(start, "start must not be null");
        Objects.requireNonNull(end, "end must not be null");
        OffsetDateTime truncatedStart = start.truncatedTo(ChronoUnit.MICROS);
        OffsetDateTime truncatedEnd = end.truncatedTo(ChronoUnit.MICROS);
        if (!truncatedEnd.isAfter(truncatedStart)) {
            // Both messages quote what the caller sent, never the truncated values.
            throw end.isAfter(start)
                    ? InvalidTimeRangeException.emptyAtStoredPrecision(start, end)
                    : new InvalidTimeRangeException(start, end);
        }
        start = truncatedStart;
        end = truncatedEnd;
    }

    public static TimeRange of(OffsetDateTime start, OffsetDateTime end) {
        return new TimeRange(start, end);
    }
}
