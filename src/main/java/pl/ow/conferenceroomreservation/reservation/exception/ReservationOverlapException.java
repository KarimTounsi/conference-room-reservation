package pl.ow.conferenceroomreservation.reservation.exception;

import org.springframework.http.HttpStatus;
import pl.ow.conferenceroomreservation.common.error.ApiException;
import pl.ow.conferenceroomreservation.common.time.TimeRange;

public class ReservationOverlapException extends ApiException {

    public ReservationOverlapException(Long roomId, TimeRange requested) {
        super(HttpStatus.CONFLICT, "/problems/reservation-overlap", "Reservation time conflict",
                "Room %d is already booked between %s and %s"
                        .formatted(roomId, requested.start(), requested.end()));
    }
}
