package pl.ow.conferenceroomreservation.reservation.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import pl.ow.conferenceroomreservation.TestcontainersConfiguration;
import pl.ow.conferenceroomreservation.common.time.TimeRange;
import pl.ow.conferenceroomreservation.reservation.entity.Reservation;
import pl.ow.conferenceroomreservation.reservation.entity.ReservationStatus;
import pl.ow.conferenceroomreservation.room.entity.ConferenceRoom;
import pl.ow.conferenceroomreservation.room.repository.ConferenceRoomRepository;

/**
 * Optimistic locking on a reservation: the version column turns a lost update into a failure the
 * API can report, instead of silently overwriting someone else's change.
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReservationOptimisticLockingTest {

    private static final OffsetDateTime NINE = OffsetDateTime.parse("2026-09-10T09:00:00+02:00");
    private static final OffsetDateTime TEN = OffsetDateTime.parse("2026-09-10T10:00:00+02:00");

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ConferenceRoomRepository conferenceRoomRepository;

    private Reservation persistReservation(String roomName) {
        ConferenceRoom room = conferenceRoomRepository.saveAndFlush(ConferenceRoom.create(roomName, 10));
        return reservationRepository.saveAndFlush(
                Reservation.create(room, "Karim", TimeRange.of(NINE, TEN)));
    }


    @Test
    void shouldFailWhenTheRowWasChangedBySomeoneElseFirst() {
        Reservation reservation = persistReservation("Lost Update Room");
        Long id = reservation.getId();

        // Someone else commits a change to the same row. Updating behind Hibernate's back is the
        // simplest faithful stand-in for a second transaction that already committed.
        entityManager.createNativeQuery("UPDATE reservation SET version = version + 1 WHERE id = :id")
                .setParameter("id", id)
                .executeUpdate();

        reservation.cancel();

        // The UPDATE carries "WHERE id = ? AND version = ?", matches no row, and Hibernate reports it.
        assertThatThrownBy(() -> reservationRepository.saveAndFlush(reservation))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
    }

}
