package pl.ow.conferenceroomreservation.common.error;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Drives every branch of the handler through a throw-away controller, so each exception family is
 * checked as an actual HTTP response rather than as a return value.
 */
@WebMvcTest(controllers = ApiExceptionHandlerTest.ProbeController.class)
@Import({ApiExceptionHandler.class, ApiExceptionHandlerTest.ProbeController.class})
class ApiExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnNotFoundForMissingResource() throws Exception {
        mockMvc.perform(get("/probe/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/problems/probe-not-found"))
                .andExpect(jsonPath("$.title").value("Probe not found"))
                .andExpect(jsonPath("$.detail").value("Probe 42 does not exist"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void shouldReturnBadRequestForBusinessRuleViolation() throws Exception {
        mockMvc.perform(get("/probe/business-rule"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/probe-rule"))
                .andExpect(jsonPath("$.title").value("Probe rule violated"));
    }

    @Test
    void shouldReturnConflictForConflictingState() throws Exception {
        mockMvc.perform(get("/probe/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/probe-conflict"))
                .andExpect(jsonPath("$.title").value("Probe conflict"));
    }

    @Test
    void shouldReturnValidationErrorsForInvalidBody() throws Exception {
        mockMvc.perform(post("/probe/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-failed"))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value(notNullValue()));
    }

    @Test
    void shouldReturnProblemDetailForMalformedJson() throws Exception {
        mockMvc.perform(post("/probe/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-failed"))
                .andExpect(jsonPath("$.detail").value(notNullValue()));
    }

    @Test
    void shouldHideInternalDetailsForUnexpectedFailures() throws Exception {
        mockMvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.detail").value(not("secret failure detail")));
    }

    @Test
    void shouldMapKnownConstraintViolationToConflict() throws Exception {
        mockMvc.perform(get("/probe/known-constraint"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/conference-room-name-taken"))
                .andExpect(jsonPath("$.title").value("Conference room name already exists"));
    }

    /**
     * Hibernate recovers a constraint name by matching English message text, so on a server
     * speaking anything else the loser of a name race arrives unnamed. SQLSTATE cannot rescue it -
     * 23505 covers the primary keys too - but the identifier inside the message can.
     */
    @Test
    void shouldMapUniqueViolationByConstraintIdentifierWhateverLanguageTheServerSpeaks() throws Exception {
        mockMvc.perform(get("/probe/localised-unique"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/conference-room-name-taken"))
                .andExpect(jsonPath("$.title").value("Conference room name already exists"));
    }

    @Test
    void shouldNotClaimConflictForAnUnrecognisedConstraint() throws Exception {
        mockMvc.perform(get("/probe/unknown-constraint"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }

    /**
     * Wraps the Hibernate exception two levels deep on purpose: the handler must walk the whole
     * cause chain, because the driver-level exception carries no constraint name.
     */
    private static DataIntegrityViolationException integrityViolation(String constraintName,
            String sqlState, String serverMessage) {
        var hibernate = new org.hibernate.exception.ConstraintViolationException(
                "could not execute statement", new SQLException(serverMessage, sqlState), constraintName);
        return new DataIntegrityViolationException("insert failed",
                new IllegalStateException("wrapper", hibernate));
    }

    static class ProbeNotFoundException extends ApiException {
        ProbeNotFoundException() {
            super(HttpStatus.NOT_FOUND, "/problems/probe-not-found", "Probe not found",
                    "Probe 42 does not exist");
        }
    }

    static class ProbeRuleException extends ApiException {
        ProbeRuleException() {
            super(HttpStatus.BAD_REQUEST, "/problems/probe-rule", "Probe rule violated",
                    "Probe rule was broken");
        }
    }

    static class ProbeConflictException extends ApiException {
        ProbeConflictException() {
            super(HttpStatus.CONFLICT, "/problems/probe-conflict", "Probe conflict",
                    "Probe already exists");
        }
    }

    record ProbeRequest(@NotBlank String name) {
    }

    @RestController
    static class ProbeController {

        @GetMapping("/probe/not-found")
        void notFound() {
            throw new ProbeNotFoundException();
        }

        @GetMapping("/probe/business-rule")
        void businessRule() {
            throw new ProbeRuleException();
        }

        @GetMapping("/probe/conflict")
        void conflict() {
            throw new ProbeConflictException();
        }

        @GetMapping("/probe/known-constraint")
        void knownConstraint() {
            throw integrityViolation("uq_conference_room_name", "23505", "duplicate key");
        }

        @GetMapping("/probe/localised-unique")
        void localisedUnique() {
            // Polish lc_messages: the text Hibernate looks for is translated, so it reports no name.
            throw integrityViolation(null, "23505",
                    "podwojna wartosc klucza narusza ograniczenie unikalnosci "
                            + "\"uq_conference_room_name\"");
        }

        @GetMapping("/probe/unknown-constraint")
        void unknownConstraint() {
            throw integrityViolation("some_other_constraint", "23505", "duplicate key");
        }

        @GetMapping("/probe/boom")
        void boom() {
            throw new IllegalStateException("secret failure detail");
        }

        @PostMapping("/probe/validate")
        void validate(@Valid @RequestBody ProbeRequest request) {
            // validation only
        }
    }
}
