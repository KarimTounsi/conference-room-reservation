package pl.ow.conferenceroomreservation.room.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A conference room")
public record ConferenceRoomResponse(Long id, String name, int maxOccupancy) {
}
