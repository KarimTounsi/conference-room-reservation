package pl.ow.conferenceroomreservation.reservation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.ow.conferenceroomreservation.reservation.dto.CreateReservationRequest;
import pl.ow.conferenceroomreservation.reservation.dto.ReservationResponse;
import pl.ow.conferenceroomreservation.reservation.entity.ReservationStatus;
import pl.ow.conferenceroomreservation.reservation.service.ReservationService;

@Tag(name = "Reservations")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReservationRestController {

    private final ReservationService reservationService;

    @Operation(summary = "Book a room",
            description = "The interval is half-open: a booking ending at 10:00 does not clash with "
                    + "one starting at 10:00.")
    // Successes are generated from the method signature. Failures must declare their content, or
    // springdoc would document them with the success schema. This endpoint carries the whole error
    // contract for the API; the other methods do not repeat it.
    @ApiResponse(responseCode = "400", description = "Validation failed, or endTime is not after startTime",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Room does not exist",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Overlaps an active reservation of that room",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/rooms/{roomId}/reservations")
    public ResponseEntity<ReservationResponse> createReservation(@PathVariable Long roomId,
            @Valid @RequestBody CreateReservationRequest request) {
        ReservationResponse created = reservationService.createReservation(roomId, request);
        return ResponseEntity.created(URI.create("/api/v1/reservations/" + created.id())).body(created);
    }

    @Operation(summary = "List a room's reservations",
            description = "Returns every reservation of the room, oldest first, cancelled ones "
                    + "included unless narrowed with status. from/to select reservations "
                    + "intersecting that window. The list is not paged - the brief asks for all of "
                    + "a room's reservations, and the date window is the intended way to narrow it.")
    @GetMapping("/rooms/{roomId}/reservations")
    public List<ReservationResponse> findReservations(@PathVariable Long roomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime to,
            @RequestParam(required = false) ReservationStatus status) {
        return reservationService.findReservations(roomId, from, to, status);
    }

    @Operation(summary = "Cancel a reservation",
            description = "Idempotent: repeating the call leaves the reservation cancelled and "
                    + "returns 204. The slot is released for new bookings; the row is kept as an "
                    + "audit trail. If two cancellations of the same reservation race, one is "
                    + "answered 409 - the reservation is still cancelled, and a retry returns 204.")
    @DeleteMapping("/reservations/{reservationId}")
    public ResponseEntity<Void> cancelReservation(@PathVariable Long reservationId) {
        reservationService.cancelReservation(reservationId);
        return ResponseEntity.noContent().build();
    }
}
