package pl.ow.conferenceroomreservation.reservation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import pl.ow.conferenceroomreservation.TestcontainersConfiguration;
import pl.ow.conferenceroomreservation.common.time.TimeRange;
import pl.ow.conferenceroomreservation.reservation.entity.Reservation;
import pl.ow.conferenceroomreservation.reservation.entity.ReservationStatus;
import pl.ow.conferenceroomreservation.room.entity.ConferenceRoom;
import pl.ow.conferenceroomreservation.room.repository.ConferenceRoomRepository;

/**
 * Drives the overlap truth table against the production query.
 *
 * <p>The rule lives in SQL and in the exclusion constraint, so this is where it is stated in full:
 * touching, disjoint, partial overlap both ways, containment both ways, identical, shared start,
 * shared end. A {@code <=} where a {@code <} belongs fails here.
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReservationRepositoryTest {

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ConferenceRoomRepository conferenceRoomRepository;

    /** The exact ordering the service asks for, so these tests exercise the production call. */
    private static final Sort BY_START_TIME = Sort.by(Sort.Direction.ASC, "startTime");

    private ConferenceRoom room;
    private ConferenceRoom otherRoom;

    private static OffsetDateTime at(String localTime) {
        return OffsetDateTime.parse("2026-09-10T" + localTime + ":00+02:00");
    }

    private static TimeRange range(String start, String end) {
        return TimeRange.of(at(start), at(end));
    }

    @BeforeEach
    void setUp() {
        room = conferenceRoomRepository.saveAndFlush(ConferenceRoom.create("Alpha", 10));
        otherRoom = conferenceRoomRepository.saveAndFlush(ConferenceRoom.create("Beta", 10));
    }

    private void bookExisting(String start, String end) {
        reservationRepository.saveAndFlush(Reservation.create(room, "Karim", range(start, end)));
    }

    @DisplayName("SQL overlap detection matches the Java truth table exactly")
    @ParameterizedTest(name = "existing [{0}-{1}] vs requested [{2}-{3}] -> overlaps={4}")
    @CsvSource({
            "09:00, 10:00, 10:00, 11:00, false",
            "10:00, 11:00, 09:00, 10:00, false",
            "09:00, 10:00, 11:00, 12:00, false",
            "09:00, 11:00, 10:00, 12:00, true",
            "10:00, 12:00, 09:00, 11:00, true",
            "09:00, 12:00, 10:00, 11:00, true",
            "10:00, 11:00, 09:00, 12:00, true",
            "09:00, 10:00, 09:00, 10:00, true",
            "09:00, 10:00, 09:00, 09:01, true",
            "09:00, 10:00, 09:59, 10:00, true",
    })
    void shouldDetectOverlapInSqlAccordingToTruthTable(String existingStart, String existingEnd,
            String requestedStart, String requestedEnd, boolean expectedOverlap) {
        bookExisting(existingStart, existingEnd);

        boolean overlaps = reservationRepository
                .existsActiveOverlapping(room.getId(), range(requestedStart, requestedEnd));

        assertThat(overlaps).isEqualTo(expectedOverlap);
    }

    @Test
    void shouldIgnoreCancelledReservationsWhenDetectingOverlap() {
        Reservation existing = reservationRepository
                .saveAndFlush(Reservation.create(room, "Karim", range("09:00", "11:00")));
        existing.cancel();
        reservationRepository.saveAndFlush(existing);

        assertThat(reservationRepository.existsActiveOverlapping(room.getId(), range("10:00", "12:00")))
                .isFalse();
    }

    @Test
    void shouldNotLetOneRoomBlockAnother() {
        bookExisting("09:00", "11:00");

        assertThat(reservationRepository.existsActiveOverlapping(otherRoom.getId(), range("09:00", "11:00")))
                .isFalse();
    }

    @Test
    void shouldReturnEveryReservationOfTheRoomWhenNoFilterIsGiven() {
        bookExisting("09:00", "10:00");
        bookExisting("11:00", "12:00");

        var found = reservationRepository.findAll(
                ReservationSpecifications.forRoom(room.getId())
                        .and(ReservationSpecifications.withStatus(null))
                        .and(ReservationSpecifications.intersecting(null, null)),
                BY_START_TIME);

        assertThat(found).hasSize(2);
    }

    @Test
    void shouldFilterByStatus() {
        bookExisting("09:00", "10:00");
        Reservation cancelled = reservationRepository
                .saveAndFlush(Reservation.create(room, "Karim", range("11:00", "12:00")));
        cancelled.cancel();
        reservationRepository.saveAndFlush(cancelled);

        var active = reservationRepository.findAll(
                ReservationSpecifications.forRoom(room.getId())
                        .and(ReservationSpecifications.withStatus(ReservationStatus.ACTIVE)),
                BY_START_TIME);

        assertThat(active).hasSize(1);
        assertThat(active.getFirst().getStartTime()).isEqualTo(at("09:00"));
    }

    @DisplayName("date window filter, every combination of present and absent bounds")
    @ParameterizedTest(name = "from={0} to={1} -> {2} match(es)")
    @CsvSource(nullValues = "null", value = {
            "null,  null,  2",
            "10:30, null,  1",
            "09:30, 11:30, 2",
            // A window disjoint from both reservations: the one case proving the filter can
            // return nothing at all.
            "12:00, 13:00, 0",
    })
    void shouldFilterByIntersectingWindow(String from, String to, int expectedMatches) {
        bookExisting("09:00", "10:00");
        bookExisting("11:00", "12:00");

        var found = reservationRepository.findAll(
                ReservationSpecifications.forRoom(room.getId())
                        .and(ReservationSpecifications.intersecting(
                                from == null ? null : at(from), to == null ? null : at(to))),
                BY_START_TIME);

        assertThat(found).hasSize(expectedMatches);
    }

    @Test
    void shouldReturnReservationsOldestFirstWhateverTheInsertionOrder() {
        bookExisting("11:00", "12:00");
        bookExisting("09:00", "10:00");

        var found = reservationRepository.findAll(
                ReservationSpecifications.forRoom(room.getId()), BY_START_TIME);

        assertThat(found).extracting(Reservation::getStartTime)
                .containsExactly(at("09:00"), at("11:00"));
    }
}
