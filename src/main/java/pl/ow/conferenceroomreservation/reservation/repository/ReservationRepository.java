package pl.ow.conferenceroomreservation.reservation.repository;

import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import pl.ow.conferenceroomreservation.common.time.TimeRange;
import pl.ow.conferenceroomreservation.reservation.entity.Reservation;
import pl.ow.conferenceroomreservation.reservation.entity.ReservationStatus;

public interface ReservationRepository
        extends JpaRepository<Reservation, Long>, JpaSpecificationExecutor<Reservation> {

    boolean existsByRoomIdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(
            Long roomId, ReservationStatus status, OffsetDateTime end, OffsetDateTime start);

    /**
     * The same condition as the database exclusion constraint:
     * {@code existing.start < requested.end AND requested.start < existing.end}. Note the arguments
     * are deliberately crossed - that is what makes the derived query express it.
     *
     * <p>Filtering on {@code room.id} reads the foreign key column, so no join is emitted, and the
     * predicate lands on {@code idx_reservation_room_start}.
     */
    default boolean existsActiveOverlapping(Long roomId, TimeRange requested) {
        return existsByRoomIdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(
                roomId, ReservationStatus.ACTIVE, requested.end(), requested.start());
    }
}
