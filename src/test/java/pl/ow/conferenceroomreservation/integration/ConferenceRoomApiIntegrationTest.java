package pl.ow.conferenceroomreservation.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import pl.ow.conferenceroomreservation.TestcontainersConfiguration;
import pl.ow.conferenceroomreservation.room.dto.ConferenceRoomResponse;
import pl.ow.conferenceroomreservation.room.dto.CreateConferenceRoomRequest;

/** End-to-end over real HTTP against a real PostgreSQL container. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class ConferenceRoomApiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldCreateThenReadBackTheRoom() {
        ResponseEntity<ConferenceRoomResponse> created = restTemplate.postForEntity(
                "/api/v1/rooms", new CreateConferenceRoomRequest("Integration Alpha", 12),
                ConferenceRoomResponse.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        assertThat(created.getHeaders().getLocation()).isNotNull();

        Long roomId = created.getBody().id();
        ResponseEntity<ConferenceRoomResponse> fetched =
                restTemplate.getForEntity("/api/v1/rooms/" + roomId, ConferenceRoomResponse.class);

        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().name()).isEqualTo("Integration Alpha");
        assertThat(fetched.getBody().maxOccupancy()).isEqualTo(12);
    }

    @Test
    void shouldRejectDuplicateRoomName() {
        restTemplate.postForEntity("/api/v1/rooms",
                new CreateConferenceRoomRequest("Integration Duplicate", 8), ConferenceRoomResponse.class);

        ResponseEntity<String> second = restTemplate.postForEntity("/api/v1/rooms",
                new CreateConferenceRoomRequest("Integration Duplicate", 8), String.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody()).contains("/problems/conference-room-name-taken");
    }

    @Test
    void shouldReturnNotFoundForUnknownRoom() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/api/v1/rooms/999999", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("/problems/conference-room-not-found");
    }

    /**
     * One rule - the request has to describe a real room - so one test, covering both fields the
     * caller can get wrong plus an id that is not a number at all.
     */
    @Test
    void shouldRejectInvalidRoomData() {
        assertThat(restTemplate.postForEntity("/api/v1/rooms",
                new CreateConferenceRoomRequest("  ", 8), String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(restTemplate.postForEntity("/api/v1/rooms",
                new CreateConferenceRoomRequest("Zero capacity", 0), String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(restTemplate.getForEntity("/api/v1/rooms/abc", String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * The brief asks for "a list of all rooms", so the endpoint answers with a bare JSON array
     * holding every room - not a page of them. Pinned because a return type is easy to widen back
     * into a Page without anyone noticing the contract changed.
     */
    @Test
    void shouldReturnEveryRoomAsAPlainArray() {
        String first = "Listing " + System.nanoTime();
        String second = "Listing " + System.nanoTime();
        restTemplate.postForEntity("/api/v1/rooms",
                new CreateConferenceRoomRequest(first, 6), ConferenceRoomResponse.class);
        restTemplate.postForEntity("/api/v1/rooms",
                new CreateConferenceRoomRequest(second, 6), ConferenceRoomResponse.class);

        ResponseEntity<ConferenceRoomResponse[]> response =
                restTemplate.getForEntity("/api/v1/rooms", ConferenceRoomResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting(ConferenceRoomResponse::name)
                .contains(first, second);

        ResponseEntity<String> raw = restTemplate.getForEntity("/api/v1/rooms", String.class);
        assertThat(raw.getBody()).startsWith("[").doesNotContain("\"totalElements\"");
    }

}
