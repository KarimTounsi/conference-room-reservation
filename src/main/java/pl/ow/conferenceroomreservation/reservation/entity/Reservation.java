package pl.ow.conferenceroomreservation.reservation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import lombok.Getter;
import pl.ow.conferenceroomreservation.common.time.TimeRange;
import pl.ow.conferenceroomreservation.room.entity.ConferenceRoom;

/**
 * A booking of one room for one half-open time interval.
 *
 * <p>The room association is lazy: listing a room's reservations must never trigger a query per
 * row, and the response only needs the foreign key, which a proxy can answer without loading.
 */
@Getter
@Entity
@Table(name = "reservation")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private ConferenceRoom room;

    @Column(name = "booked_by", nullable = false, length = 200)
    private String bookedBy;

    @Column(name = "start_time", nullable = false)
    private OffsetDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private OffsetDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Version
    private long version;

    protected Reservation() {
        // for JPA
    }

    private Reservation(ConferenceRoom room, String bookedBy, TimeRange timeRange) {
        this.room = room;
        this.bookedBy = bookedBy;
        this.startTime = timeRange.start();
        this.endTime = timeRange.end();
        this.status = ReservationStatus.ACTIVE;
    }

    public static Reservation create(ConferenceRoom room, String bookedBy, TimeRange timeRange) {
        return new Reservation(room, bookedBy, timeRange);
    }

    /**
     * Cancelling an already cancelled reservation is a no-op: no field changes, so Hibernate issues
     * no UPDATE and the version does not move. Repeating DELETE is therefore harmless.
     *
     * <p>Idempotent in the HTTP sense - the effect on server state is the same however many times
     * the call is made. Two DELETEs racing on the same active reservation both read version n and
     * both try to write; one commits and the loser is answered 409 "modified concurrently". The
     * resource still ends up CANCELLED, which is what the caller wanted, and a retry returns 204.
     */
    public void cancel() {
        if (status == ReservationStatus.CANCELLED) {
            return;
        }
        status = ReservationStatus.CANCELLED;
    }
}
