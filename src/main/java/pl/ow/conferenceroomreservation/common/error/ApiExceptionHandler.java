package pl.ow.conferenceroomreservation.common.error;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates exceptions into RFC 9457 problem responses. Extending
 * {@link ResponseEntityExceptionHandler} makes Spring MVC own failures - unreadable body, missing
 * parameter, wrong parameter type - come back in the same shape as domain failures, so a client
 * parses one error format.
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    static final String VALIDATION_FAILED_TYPE = "/problems/validation-failed";

    /**
     * Constraints that encode a business rule. Backstops only: the services reject the same
     * situations earlier and with a fuller message, so this path runs when two requests race.
     */
    private static final ConstraintProblem ROOM_NAME_TAKEN = new ConstraintProblem(
            "/problems/conference-room-name-taken",
            "Conference room name already exists",
            "A conference room with that name already exists");

    private static final ConstraintProblem RESERVATION_OVERLAP = new ConstraintProblem(
            "/problems/reservation-overlap",
            "Reservation time conflict",
            // Deliberately general: reaching this path means two requests raced, and the handler
            // sees only the exception - not the room, not the requested interval.
            "The room is already booked for the requested time range");

    private static final Map<String, ConstraintProblem> KNOWN_CONSTRAINTS = Map.of(
            "uq_conference_room_name", ROOM_NAME_TAKEN,
            "excl_reservation_active_overlap", RESERVATION_OVERLAP);

    /**
     * The one violation whose message names nothing in any language: Hibernate finds constraint
     * names by matching the literal text {@code constraint "}, which an exclusion violation never
     * contains. Keying on SQLSTATE is safe only because the schema declares exactly one exclusion
     * constraint. Nothing else belongs here - 23505 covers the primary keys as well as the room
     * name, so mapping it wholesale would dress a server fault as a business conflict.
     */
    private static final Map<String, ConstraintProblem> BY_SQL_STATE = Map.of(
            "23P01", RESERVATION_OVERLAP);

    private record ConstraintProblem(String type, String title, String detail) {
    }

    /** Every failure the API describes to the caller: the exception itself carries its status. */
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApiException(ApiException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(exception.getStatus());
        problem.setType(URI.create(exception.getType()));
        problem.setTitle(exception.getTitle());
        problem.setDetail(exception.getMessage());
        // Only the type and the status are logged: the detail can quote what the caller sent.
        log.debug("{} -> {}", exception.getType(), exception.getStatus().value());
        return ResponseEntity.status(exception.getStatus()).body(problem);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        org.hibernate.exception.ConstraintViolationException violation = findConstraintViolation(exception);
        ConstraintProblem known = resolveKnownConstraint(violation);
        if (known == null) {
            // An integrity violation we did not design as a business rule is a bug, not a 409.
            log.error("Unmapped data integrity violation (constraint={} sqlState={})",
                    violation == null ? null : violation.getConstraintName(),
                    violation == null ? null : violation.getSQLState(),
                    exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(internalServerError());
        }
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setType(URI.create(known.type()));
        problem.setTitle(known.title());
        problem.setDetail(known.detail());
        log.debug("Constraint violation mapped to 409 ({})", known.type());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    /**
     * The name Hibernate recovered decides, and an unrecognised name stops the search - some other
     * constraint failed. Hibernate finds names by matching English message text, so on a server
     * speaking anything else it reports none; the identifier is SQL and is never translated, so the
     * first line of the message is searched for it. Only the first line: the ones below echo values
     * the caller chose, and a room named after a constraint must not steer the answer.
     */
    private ConstraintProblem resolveKnownConstraint(
            org.hibernate.exception.ConstraintViolationException violation) {
        if (violation == null) {
            return null;
        }
        String name = violation.getConstraintName();
        if (name != null) {
            return KNOWN_CONSTRAINTS.get(name);
        }
        ConstraintProblem bySqlState = BY_SQL_STATE.get(violation.getSQLState());
        if (bySqlState != null) {
            return bySqlState;
        }
        String message = violation.getSQLException() == null
                ? violation.getMessage()
                : violation.getSQLException().getMessage();
        if (message == null) {
            return null;
        }
        int lineBreak = message.indexOf('\n');
        String firstLine = lineBreak < 0 ? message : message.substring(0, lineBreak);
        List<ConstraintProblem> matches = KNOWN_CONSTRAINTS.entrySet().stream()
                .filter(entry -> firstLine.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    /** getMostSpecificCause() would return the driver exception, which carries no constraint name. */
    private org.hibernate.exception.ConstraintViolationException findConstraintViolation(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof org.hibernate.exception.ConstraintViolationException violation) {
                return violation;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return null;
    }

    /**
     * Both concurrency failures a caller can hit, answered identically.
     *
     * <p>{@link OptimisticLockingFailureException} means someone changed the row first.
     * {@link PessimisticLockingFailureException} means the database killed this statement as a
     * deadlock victim: when many requests insert into the same excluded slot at once they queue on
     * the GiST index, and PostgreSQL resolves the tangle by aborting some of them. The application
     * takes no pessimistic locks of its own. Either way the request lost to concurrent activity on
     * the same resource, which is what 409 means.
     */
    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    ResponseEntity<ProblemDetail> handleConcurrencyFailure(Exception exception) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setType(URI.create("/problems/concurrent-modification"));
        problem.setTitle("Concurrent modification");
        problem.setDetail("The resource was modified concurrently, please retry");
        // The exception message can be a multi-line report from the server, so only the type is logged.
        log.debug("Concurrency failure ({})", exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        log.error("Unhandled exception", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(internalServerError());
    }

    /** RFC 9457 keeps about:blank for a plain status with no extra semantics. */
    private ProblemDetail internalServerError() {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Internal Server Error");
        problem.setDetail("An unexpected error occurred");
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create(VALIDATION_FAILED_TYPE));
        problem.setTitle("Validation failed");
        problem.setDetail("The request body failed validation");
        problem.setProperty("errors", fieldErrors(exception));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /** One stable answer for a body that could not be bound, without echoing what was sent. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create(VALIDATION_FAILED_TYPE));
        problem.setTitle("Validation failed");
        problem.setDetail("Request body is not valid JSON or contains a value of the wrong type");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    private List<Map<String, String>> fieldErrors(MethodArgumentNotValidException exception) {
        return exception.getBindingResult().getAllErrors().stream()
                .map(error -> Map.of(
                        "field", error instanceof org.springframework.validation.FieldError fieldError
                                ? fieldError.getField()
                                : error.getObjectName(),
                        "message", String.valueOf(error.getDefaultMessage())))
                .sorted(Comparator.comparing(error -> error.get("field")))
                .toList();
    }
}
