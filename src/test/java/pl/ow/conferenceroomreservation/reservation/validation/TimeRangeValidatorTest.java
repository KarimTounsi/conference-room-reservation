package pl.ow.conferenceroomreservation.reservation.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.ow.conferenceroomreservation.reservation.dto.CreateReservationRequest;

class TimeRangeValidatorTest {

    private static final OffsetDateTime NINE = OffsetDateTime.parse("2026-09-10T09:00:00+02:00");
    private static final OffsetDateTime TEN = OffsetDateTime.parse("2026-09-10T10:00:00+02:00");

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void shouldAcceptRangeWhereEndIsAfterStart() {
        assertThat(validator.validate(new CreateReservationRequest("Karim", NINE, TEN))).isEmpty();
    }

    @Test
    void shouldRejectRangeWhereEndEqualsStart() {
        var violations = validator.validate(new CreateReservationRequest("Karim", NINE, NINE));

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.getPropertyPath()).hasToString("endTime");
            assertThat(violation.getMessage()).isEqualTo("endTime must be after startTime");
        });
    }

    @Test
    void shouldRejectRangeWhereEndIsBeforeStart() {
        assertThat(validator.validate(new CreateReservationRequest("Karim", TEN, NINE))).hasSize(1);
    }

    @Test
    void shouldLeaveNullBoundsToNotNullSoOneMistakeYieldsOneError() {
        var missingStart = validator.validate(new CreateReservationRequest("Karim", null, TEN));
        var missingEnd = validator.validate(new CreateReservationRequest("Karim", NINE, null));

        assertThat(missingStart).singleElement()
                .satisfies(v -> assertThat(v.getPropertyPath()).hasToString("startTime"));
        assertThat(missingEnd).singleElement()
                .satisfies(v -> assertThat(v.getPropertyPath()).hasToString("endTime"));
    }

    @Test
    void shouldReportBlankBookedBy() {
        var violations = validator.validate(new CreateReservationRequest("  ", NINE, TEN));

        assertThat(violations).singleElement()
                .satisfies(v -> assertThat(v.getPropertyPath()).hasToString("bookedBy"));
    }
}
