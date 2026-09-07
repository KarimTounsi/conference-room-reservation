package pl.ow.conferenceroomreservation.reservation.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import pl.ow.conferenceroomreservation.reservation.dto.CreateReservationRequest;

/**
 * Rejects the request at the edge, so a bad range never reaches the service.
 *
 * <p>Null bounds pass here on purpose - {@code @NotNull} reports those on their own fields, and
 * reporting them twice would produce two errors for one mistake. The rule is enforced a second time
 * by {@code TimeRange}, which is what protects callers that never went through bean validation.
 */
public class TimeRangeValidator implements ConstraintValidator<ValidTimeRange, CreateReservationRequest> {

    @Override
    public boolean isValid(CreateReservationRequest request, ConstraintValidatorContext context) {
        if (request == null || request.startTime() == null || request.endTime() == null) {
            return true;
        }
        if (request.endTime().isAfter(request.startTime())) {
            return true;
        }
        // Point the violation at endTime so the error list names a field, not the whole object.
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("endTime")
                .addConstraintViolation();
        return false;
    }
}
