package pl.ow.conferenceroomreservation.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import pl.ow.conferenceroomreservation.TestcontainersConfiguration;

/**
 * Proves the Flyway schema and the JPA entities agree, and that the database enforces the booking
 * invariants on its own - including for writes that never go through the application.
 *
 * <p>The context only starts if {@code ddl-auto=validate} accepts the migrated schema, so simply
 * loading this test is already an assertion.
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SchemaMigrationTest {

    private static final OffsetDateTime NINE = OffsetDateTime.parse("2026-09-10T09:00:00+02:00");
    private static final OffsetDateTime TEN = OffsetDateTime.parse("2026-09-10T10:00:00+02:00");

    @Autowired
    private EntityManager entityManager;

    /** Inserts natively so these tests exercise the schema, not the mapping layer above it. */
    private Long insertRoom(String name) {
        entityManager.createNativeQuery(
                        "INSERT INTO conference_room (name, max_occupancy) VALUES (:name, 10)")
                .setParameter("name", name)
                .executeUpdate();
        return ((Number) entityManager
                .createNativeQuery("SELECT id FROM conference_room WHERE name = :name")
                .setParameter("name", name)
                .getSingleResult()).longValue();
    }

    private void insertReservation(Long roomId, OffsetDateTime start, OffsetDateTime end, String status) {
        entityManager.createNativeQuery("""
                        INSERT INTO reservation (room_id, booked_by, start_time, end_time, status, version)
                        VALUES (:roomId, :bookedBy, :start, :end, :status, 0)
                        """)
                .setParameter("roomId", roomId)
                .setParameter("bookedBy", "Karim")
                .setParameter("start", start)
                .setParameter("end", end)
                .setParameter("status", status)
                .executeUpdate();
    }

    private static void assertViolates(Throwable thrown, String constraintName) {
        for (Throwable current = thrown; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains(constraintName)) {
                return;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        throw new AssertionError("Expected a violation of " + constraintName + " but got: " + thrown);
    }

    @Test
    void shouldRejectReservationWhoseEndIsNotAfterStart() {
        Long roomId = insertRoom("Zeta");

        assertThatThrownBy(() -> insertReservation(roomId, TEN, NINE, "ACTIVE"))
                .satisfies(thrown -> assertViolates(thrown, "chk_reservation_time_range"));
    }

    @Test
    void shouldRejectUnknownReservationStatus() {
        Long roomId = insertRoom("Eta");

        assertThatThrownBy(() -> insertReservation(roomId, NINE, TEN, "ACTVE"))
                .satisfies(thrown -> assertViolates(thrown, "chk_reservation_status"));
    }

    @Test
    void shouldRejectDuplicateRoomName() {
        insertRoom("Theta");

        assertThatThrownBy(() -> insertRoom("Theta"))
                .satisfies(thrown -> assertViolates(thrown, "uq_conference_room_name"));
    }
}
