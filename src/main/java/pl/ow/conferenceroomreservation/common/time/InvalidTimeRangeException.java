package pl.ow.conferenceroomreservation.common.time;

import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import pl.ow.conferenceroomreservation.common.error.ApiException;

/**
 * The end of a time range is not strictly after its start.
 *
 * <p>An {@link ApiException} rather than an {@link IllegalArgumentException} so the rule is
 * answered with HTTP 400 no matter who calls {@link TimeRange#of}, not only requests that passed
 * bean validation first.
 */
public class InvalidTimeRangeException extends ApiException {

    private InvalidTimeRangeException(String detail) {
        super(HttpStatus.BAD_REQUEST, "/problems/invalid-time-range", "Invalid time range", detail);
    }

    public InvalidTimeRangeException(OffsetDateTime start, OffsetDateTime end) {
        this("endTime must be after startTime, but was %s and %s".formatted(end, start));
    }

    /** Creates an error for an invalid optional {@code from}/{@code to} listing window. */
    public static InvalidTimeRangeException forFilterWindow(OffsetDateTime from, OffsetDateTime to) {
        return new InvalidTimeRangeException(
                "to must be after from, but was %s and %s".formatted(to, from));
    }

    /**
     * The caller's end really is after their start, but not once both are reduced to the precision
     * reservations are stored at. Reporting the truncated values here would echo two identical
     * timestamps, neither of them what was sent, so this message keeps the originals.
     */
    static InvalidTimeRangeException emptyAtStoredPrecision(OffsetDateTime start, OffsetDateTime end) {
        return new InvalidTimeRangeException(
                ("endTime must still be after startTime once both are reduced to microseconds, "
                        + "the precision reservations are stored at, but %s and %s fall in the "
                        + "same microsecond").formatted(start, end));
    }
}
