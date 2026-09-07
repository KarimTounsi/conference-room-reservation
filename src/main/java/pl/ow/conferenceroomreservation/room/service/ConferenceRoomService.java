package pl.ow.conferenceroomreservation.room.service;

import java.util.List;
import pl.ow.conferenceroomreservation.room.dto.ConferenceRoomResponse;
import pl.ow.conferenceroomreservation.room.dto.CreateConferenceRoomRequest;
import pl.ow.conferenceroomreservation.room.exception.ConferenceRoomNameAlreadyExistsException;
import pl.ow.conferenceroomreservation.room.exception.ConferenceRoomNotFoundException;

/** Conference room operations. The controller talks only to this contract, never to entities. */
public interface ConferenceRoomService {

    /**
     * @throws ConferenceRoomNameAlreadyExistsException when a room with that name already exists
     */
    ConferenceRoomResponse createRoom(CreateConferenceRoomRequest request);

    /**
     * Every room, ordered by name. The brief asks for "a list of all rooms", so the whole list is
     * what comes back - a room is a physical space, so this collection grows at the speed of
     * building work, not of usage.
     */
    List<ConferenceRoomResponse> findRooms();

    /**
     * @throws ConferenceRoomNotFoundException when no room has that id
     */
    ConferenceRoomResponse findRoom(Long roomId);
}
