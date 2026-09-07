package pl.ow.conferenceroomreservation.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import pl.ow.conferenceroomreservation.reservation.validation.ValidTimeRange;

@ValidTimeRange
@Schema(description = "Request to book a room for a half-open [start, end) interval")
public record CreateReservationRequest(

        @Schema(example = "Karim Tounsi")
        @NotBlank @Size(max = 200)
        String bookedBy,

        @Schema(example = "2026-09-10T09:00:00Z")
        @NotNull
        OffsetDateTime startTime,

        @Schema(description = "Exclusive - a booking ending at 10:00 leaves the room free from 10:00",
                example = "2026-09-10T10:00:00Z")
        @NotNull
        OffsetDateTime endTime) {
}
