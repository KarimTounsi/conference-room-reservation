package pl.ow.conferenceroomreservation.common.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class TimeRangeTest {

    private static OffsetDateTime at(String localTime) {
        return OffsetDateTime.parse("2026-09-10T" + localTime + ":00+02:00");
    }

    @Test
    void shouldRejectRangeWhenEndEqualsStart() {
        OffsetDateTime instant = at("09:00");

        assertThatThrownBy(() -> TimeRange.of(instant, instant))
                .isInstanceOf(InvalidTimeRangeException.class)
                .hasMessageContaining("endTime must be after startTime");
    }

    @Test
    void shouldRejectRangeWhenBoundIsNull() {
        OffsetDateTime instant = at("09:00");

        assertThatThrownBy(() -> TimeRange.of(null, instant)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> TimeRange.of(instant, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldCompareByInstantNotByOffset() {
        // 09:00+02:00 and 07:00Z are the same moment written two ways. Comparing with equals would
        // call them different and accept an empty range; comparing instants rejects it.
        OffsetDateTime sameMomentInUtc = OffsetDateTime.parse("2026-09-10T07:00:00Z");
        OffsetDateTime sameMomentInWarsaw = OffsetDateTime.parse("2026-09-10T09:00:00+02:00");

        assertThatThrownBy(() -> TimeRange.of(sameMomentInUtc, sameMomentInWarsaw))
                .isInstanceOf(InvalidTimeRangeException.class);
    }

    @Test
    void shouldAcceptSmallestRangeTheDatabaseCanStore() {
        assertThatCode(() -> TimeRange.of(at("09:00"), at("09:00").plusNanos(1_000)))
                .doesNotThrowAnyException();
    }

    /**
     * A range narrower than one microsecond survives an OffsetDateTime comparison but collapses to
     * nothing in TIMESTAMPTZ. Rejecting it here keeps the caller's mistake a 400 instead of letting
     * the database check constraint turn it into a 500.
     */
    @Test
    void shouldRejectRangeThatIsEmptyAtStoredPrecision() {
        assertThatThrownBy(() -> TimeRange.of(at("09:00").plusNanos(100), at("09:00").plusNanos(200)))
                .isInstanceOf(InvalidTimeRangeException.class);
    }

    @Test
    void shouldTruncateBothEndsToStoredPrecision() {
        TimeRange range = TimeRange.of(at("09:00").plusNanos(1_900), at("10:00").plusNanos(1_900));

        // Truncated, not rounded: an instant never moves forward into time nobody asked for.
        assertThat(range.start()).isEqualTo(at("09:00").plusNanos(1_000));
        assertThat(range.end()).isEqualTo(at("10:00").plusNanos(1_000));
    }
}
