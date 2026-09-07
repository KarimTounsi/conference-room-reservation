package pl.ow.conferenceroomreservation.room.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * A bookable conference room.
 *
 * <p>Deliberately carries no {@code @Version}: the API exposes no way to modify a room, so a
 * version column would be a schema field nothing ever increments. It would be added together with
 * an update endpoint, not before.
 */
@Getter
@Entity
@Table(name = "conference_room")
public class ConferenceRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "max_occupancy", nullable = false)
    private int maxOccupancy;

    protected ConferenceRoom() {
        // for JPA
    }

    private ConferenceRoom(String name, int maxOccupancy) {
        this.name = name;
        this.maxOccupancy = maxOccupancy;
    }

    public static ConferenceRoom create(String name, int maxOccupancy) {
        return new ConferenceRoom(name, maxOccupancy);
    }
}
