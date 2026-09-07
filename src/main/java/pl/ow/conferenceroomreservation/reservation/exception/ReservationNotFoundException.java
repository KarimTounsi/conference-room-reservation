package pl.ow.conferenceroomreservation.reservation.exception;

import org.springframework.http.HttpStatus;
import pl.ow.conferenceroomreservation.common.error.ApiException;

public class ReservationNotFoundException extends ApiException {

    public ReservationNotFoundException(Long reservationId) {
        super(HttpStatus.NOT_FOUND, "/problems/reservation-not-found", "Reservation not found",
                "Reservation with id %d does not exist".formatted(reservationId));
    }
}
