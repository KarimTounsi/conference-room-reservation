package pl.ow.conferenceroomreservation.room.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.ow.conferenceroomreservation.room.dto.ConferenceRoomResponse;
import pl.ow.conferenceroomreservation.room.dto.CreateConferenceRoomRequest;
import pl.ow.conferenceroomreservation.room.service.ConferenceRoomService;

@Tag(name = "Conference rooms")
@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class ConferenceRoomRestController {

    private final ConferenceRoomService conferenceRoomService;

    @Operation(summary = "Add a conference room")
    @PostMapping
    public ResponseEntity<ConferenceRoomResponse> createRoom(
            @Valid @RequestBody CreateConferenceRoomRequest request) {
        ConferenceRoomResponse created = conferenceRoomService.createRoom(request);
        return ResponseEntity.created(URI.create("/api/v1/rooms/" + created.id())).body(created);
    }

    @Operation(summary = "List all conference rooms",
            description = "Returns every room, ordered by name. The list is not paged: a room is a "
                    + "physical space, so the collection grows at the speed of building work.")
    @GetMapping
    public List<ConferenceRoomResponse> findRooms() {
        return conferenceRoomService.findRooms();
    }

    @Operation(summary = "Get one conference room")
    @GetMapping("/{roomId}")
    public ConferenceRoomResponse findRoom(@PathVariable Long roomId) {
        return conferenceRoomService.findRoom(roomId);
    }
}
