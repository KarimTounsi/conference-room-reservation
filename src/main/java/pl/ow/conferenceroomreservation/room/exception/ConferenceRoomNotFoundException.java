package pl.ow.conferenceroomreservation.room.exception;

import org.springframework.http.HttpStatus;
import pl.ow.conferenceroomreservation.common.error.ApiException;

public class ConferenceRoomNotFoundException extends ApiException {

    public ConferenceRoomNotFoundException(Long roomId) {
        super(HttpStatus.NOT_FOUND, "/problems/conference-room-not-found", "Conference room not found",
                "Conference room with id %d does not exist".formatted(roomId));
    }
}
