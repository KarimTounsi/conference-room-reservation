package pl.ow.conferenceroomreservation.room.exception;

import org.springframework.http.HttpStatus;
import pl.ow.conferenceroomreservation.common.error.ApiException;

public class ConferenceRoomNameAlreadyExistsException extends ApiException {

    public ConferenceRoomNameAlreadyExistsException(String name) {
        super(HttpStatus.CONFLICT, "/problems/conference-room-name-taken",
                "Conference room name already exists",
                "Conference room '%s' already exists".formatted(name));
    }
}
