package pl.ow.conferenceroomreservation.room.mapper;

import org.springframework.stereotype.Component;
import pl.ow.conferenceroomreservation.room.dto.ConferenceRoomResponse;
import pl.ow.conferenceroomreservation.room.entity.ConferenceRoom;

/**
 * Entity to response mapping, written by hand.
 *
 * <p>One direction only: building an entity is a domain operation and belongs to the entity's own
 * factory, where the invariants live.
 */
@Component
public class ConferenceRoomMapper {

    public ConferenceRoomResponse toResponse(ConferenceRoom room) {
        return new ConferenceRoomResponse(room.getId(), room.getName(), room.getMaxOccupancy());
    }
}
