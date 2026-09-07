package pl.ow.conferenceroomreservation.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import pl.ow.conferenceroomreservation.reservation.entity.ReservationStatus;

@Schema(description = "A room booking")
public record ReservationResponse(Long id, Long roomId, String bookedBy, OffsetDateTime startTime,
                                  OffsetDateTime endTime, ReservationStatus status) {
}
