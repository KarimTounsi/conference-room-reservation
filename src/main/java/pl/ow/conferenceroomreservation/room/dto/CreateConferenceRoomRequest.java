package pl.ow.conferenceroomreservation.room.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Request to add a conference room")
public record CreateConferenceRoomRequest(

        @Schema(example = "Alpha")
        @NotBlank @Size(max = 100)
        String name,

        @Schema(description = "Maximum number of people the room holds", example = "12")
        @NotNull @Positive
        Integer maxOccupancy) {
}
