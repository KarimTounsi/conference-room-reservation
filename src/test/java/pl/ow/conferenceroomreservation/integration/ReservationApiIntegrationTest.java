package pl.ow.conferenceroomreservation.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import pl.ow.conferenceroomreservation.TestcontainersConfiguration;
import pl.ow.conferenceroomreservation.reservation.dto.CreateReservationRequest;
import pl.ow.conferenceroomreservation.reservation.dto.ReservationResponse;
import pl.ow.conferenceroomreservation.room.dto.ConferenceRoomResponse;
import pl.ow.conferenceroomreservation.room.dto.CreateConferenceRoomRequest;

/** The whole booking story over real HTTP against real PostgreSQL. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class ReservationApiIntegrationTest {

    private static final OffsetDateTime NINE = OffsetDateTime.parse("2026-09-10T09:00:00+02:00");
    private static final OffsetDateTime TEN = OffsetDateTime.parse("2026-09-10T10:00:00+02:00");
    private static final OffsetDateTime ELEVEN = OffsetDateTime.parse("2026-09-10T11:00:00+02:00");

    @Autowired
    private TestRestTemplate restTemplate;

    private Long createRoom(String name) {
        ResponseEntity<ConferenceRoomResponse> response = restTemplate.postForEntity("/api/v1/rooms",
                new CreateConferenceRoomRequest(name, 10), ConferenceRoomResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().id();
    }

    private ResponseEntity<String> book(Long roomId, OffsetDateTime start, OffsetDateTime end) {
        return restTemplate.postForEntity("/api/v1/rooms/" + roomId + "/reservations",
                new CreateReservationRequest("Karim", start, end), String.class);
    }

    private ResponseEntity<ReservationResponse> bookForReal(Long roomId, OffsetDateTime start,
            OffsetDateTime end) {
        return restTemplate.postForEntity("/api/v1/rooms/" + roomId + "/reservations",
                new CreateReservationRequest("Karim", start, end), ReservationResponse.class);
    }

    @Test
    void shouldWalkTheWholeBookingLifecycle() {
        Long roomId = createRoom("Lifecycle Room");

        // 1. book 09:00-10:00
        ResponseEntity<ReservationResponse> first = bookForReal(roomId, NINE, TEN);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getHeaders().getLocation()).isNotNull();
        Long reservationId = first.getBody().id();

        // 2. an overlapping booking is refused
        ResponseEntity<String> overlapping = book(roomId, NINE, ELEVEN);
        assertThat(overlapping.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(overlapping.getBody()).contains("/problems/reservation-overlap");

        // 3. a booking that merely touches it is accepted - the interval is half-open
        assertThat(book(roomId, TEN, ELEVEN).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // 4. cancelling releases the slot, and repeating the call stays successful
        assertThat(restTemplate.exchange("/api/v1/reservations/" + reservationId,
                org.springframework.http.HttpMethod.DELETE, null, Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(restTemplate.exchange("/api/v1/reservations/" + reservationId,
                org.springframework.http.HttpMethod.DELETE, null, Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        // 5. the freed slot can be booked again
        assertThat(book(roomId, NINE, TEN).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void shouldListEveryReservationOfTheRoomIncludingCancelledOnes() {
        Long roomId = createRoom("Listing Room");
        Long cancelled = bookForReal(roomId, NINE, TEN).getBody().id();
        bookForReal(roomId, TEN, ELEVEN);
        restTemplate.delete("/api/v1/reservations/" + cancelled);

        ResponseEntity<String> all =
                restTemplate.getForEntity("/api/v1/rooms/" + roomId + "/reservations", String.class);

        assertThat(all.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(all.getBody()).contains("CANCELLED").contains("ACTIVE");
    }

    @Test
    void shouldNarrowTheListByStatus() {
        Long roomId = createRoom("Status Filter Room");
        Long cancelled = bookForReal(roomId, NINE, TEN).getBody().id();
        bookForReal(roomId, TEN, ELEVEN);
        restTemplate.delete("/api/v1/reservations/" + cancelled);

        ResponseEntity<String> active = restTemplate.getForEntity(
                "/api/v1/rooms/" + roomId + "/reservations?status=ACTIVE", String.class);

        assertThat(active.getBody()).contains("ACTIVE").doesNotContain("CANCELLED");
    }

    @Test
    void shouldNarrowTheListByDateWindow() {
        Long roomId = createRoom("Date Filter Room");
        bookForReal(roomId, NINE, TEN);
        bookForReal(roomId, TEN, ELEVEN);

        // Expressed in UTC so the query string carries no "+", which would need double escaping.
        // 10:00+02:00 is 08:00Z and 11:00+02:00 is 09:00Z - the same instants.
        ResponseEntity<String> window = restTemplate.getForEntity("/api/v1/rooms/" + roomId
                + "/reservations?from=2026-09-10T08:00:00Z&to=2026-09-10T09:00:00Z", String.class);

        assertThat(window.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(window.getBody())
                .as("only the 10:00-11:00 booking intersects the window")
                .containsOnlyOnce("\"id\":");
    }

    @Test
    void shouldRejectBookingForUnknownRoom() {
        ResponseEntity<String> response = book(999999L, NINE, TEN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("/problems/conference-room-not-found");
    }

    @Test
    void shouldRejectBookingWhoseEndIsNotAfterStart() {
        Long roomId = createRoom("Bad Range Room");

        ResponseEntity<String> response = book(roomId, TEN, NINE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("endTime must be after startTime");
    }
    /**
     * "All reservations for a room" comes back as a bare array, oldest first - the order has to be
     * the query's, not whatever the planner returned.
     */
    @Test
    void shouldReturnEveryReservationAsAPlainArrayOldestFirst() {
        Long roomId = createRoom("Array Listing Room");
        bookForReal(roomId, TEN, ELEVEN);
        bookForReal(roomId, NINE, TEN);

        ResponseEntity<ReservationResponse[]> response = restTemplate.getForEntity(
                "/api/v1/rooms/" + roomId + "/reservations", ReservationResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting(ReservationResponse::startTime)
                .containsExactly(NINE.withOffsetSameInstant(java.time.ZoneOffset.UTC),
                        TEN.withOffsetSameInstant(java.time.ZoneOffset.UTC));

        ResponseEntity<String> raw = restTemplate.getForEntity(
                "/api/v1/rooms/" + roomId + "/reservations", String.class);
        assertThat(raw.getBody()).startsWith("[").doesNotContain("\"totalElements\"");
    }

}
