package pl.ow.conferenceroomreservation.reservation.service;

import java.time.OffsetDateTime;
import java.util.List;
import pl.ow.conferenceroomreservation.reservation.dto.CreateReservationRequest;
import pl.ow.conferenceroomreservation.reservation.dto.ReservationResponse;
import pl.ow.conferenceroomreservation.reservation.entity.ReservationStatus;
import pl.ow.conferenceroomreservation.reservation.exception.ReservationNotFoundException;
import pl.ow.conferenceroomreservation.reservation.exception.ReservationOverlapException;
import pl.ow.conferenceroomreservation.room.exception.ConferenceRoomNotFoundException;

public interface ReservationService {

    /**
     * @throws ConferenceRoomNotFoundException when the room does not exist
     * @throws ReservationOverlapException     when an active reservation of that room overlaps
     */
    ReservationResponse createReservation(Long roomId, CreateReservationRequest request);

    /**
     * Lists a room's reservations, oldest first. All filters are optional; omitting {@code status}
     * returns every reservation, cancelled ones included, which is what "all reservations for a
     * room" means.
     *
     * @throws ConferenceRoomNotFoundException when the room does not exist
     */
    List<ReservationResponse> findReservations(Long roomId, OffsetDateTime from, OffsetDateTime to,
            ReservationStatus status);

    /**
     * Cancels a reservation, releasing its slot. Idempotent.
     *
     * @throws ReservationNotFoundException when no reservation has that id
     */
    void cancelReservation(Long reservationId);
}
