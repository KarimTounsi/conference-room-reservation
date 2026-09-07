package pl.ow.conferenceroomreservation.reservation.mapper;

import org.springframework.stereotype.Component;
import pl.ow.conferenceroomreservation.reservation.dto.ReservationResponse;
import pl.ow.conferenceroomreservation.reservation.entity.Reservation;

@Component
public class ReservationMapper {

    public ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                // Reading only the identifier of a lazy association does not initialise the proxy,
                // so listing reservations never turns into one query per row.
                reservation.getRoom().getId(),
                reservation.getBookedBy(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getStatus());
    }
}
