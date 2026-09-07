package pl.ow.conferenceroomreservation.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI conferenceRoomReservationApi() {
        return new OpenAPI().info(new Info()
                .title("Conference room reservation API")
                .version("v1")
                .description("""
                        Manage conference rooms and their bookings.

                        **Time intervals are half-open, [start, end).** A booking that ends at 10:00 \
                        leaves the room free from 10:00, so it does not clash with one starting then. \
                        Two bookings overlap exactly when `a.start < b.end AND b.start < a.end`.

                        **Timestamps carry an offset** (ISO-8601, e.g. `2026-09-10T09:00:00Z`, \
                        or any other offset such as `2026-09-10T11:00:00+02:00`, which is the \
                        same instant) and are stored as `timestamptz`, so bookings stay \
                        unambiguous across time zones and daylight-saving changes.

                        **Errors are RFC 9457 problem responses.** Each carries a stable `type` URI \
                        clients may branch on; the `detail` text is not part of the contract.

                        **Cancelling is a status change, not a delete.** The reservation is kept as an \
                        audit trail and its slot becomes bookable again. Repeating the call is safe.

                        **Responses always report UTC.** A request may use any offset - \
                        `10:00+02:00` and `08:00Z` are the same instant, and the instant is what \
                        is stored and returned. The API neither echoes the caller's offset nor \
                        invents one from the server's own zone: a room's time zone is not part of \
                        this model, so UTC is the only value the API can state with certainty.
                        """))
                // Declared, rather than left to the order in which controllers happen to be
                // scanned, so the reader meets rooms before the bookings that depend on them.
                .tags(List.of(
                        new Tag().name("Conference rooms")
                                .description("Add a conference room, and list all rooms."),
                        new Tag().name("Reservations")
                                .description("Book a room, list a room's reservations, and "
                                        + "cancel a booking.")));
    }
}
