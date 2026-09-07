package pl.ow.conferenceroomreservation.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import pl.ow.conferenceroomreservation.TestcontainersConfiguration;

/**
 * Checks that the generated contract is served and describes the endpoints a client needs - not
 * that every status of every method is spelled out, which springdoc derives from the signatures.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class OpenApiContractTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private DocumentContext apiDocs() {
        ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return JsonPath.parse(response.getBody());
    }

    @Test
    void shouldExposeOpenApiDocument() {
        DocumentContext document = apiDocs();

        assertThat(document.read("$.openapi", String.class)).startsWith("3.");
        assertThat(document.read("$.info.title", String.class))
                .isEqualTo("Conference room reservation API");
        assertThat(document.read("$.info.description", String.class))
                .contains("half-open");
    }

    /**
     * The booking endpoint carries the error contract for the whole API, so it is the one place
     * where the failure responses have to stay documented as problem responses.
     */
    @Test
    void shouldDocumentCoreRoomAndReservationEndpoints() {
        DocumentContext document = apiDocs();

        Map<String, Object> paths = document.read("$.paths");
        assertThat(paths).containsKeys("/api/v1/rooms", "/api/v1/rooms/{roomId}",
                "/api/v1/rooms/{roomId}/reservations", "/api/v1/reservations/{reservationId}");

        String booking = "$.paths.['/api/v1/rooms/{roomId}/reservations'].post.responses";
        Map<String, Object> bookingResponses = document.read(booking);
        assertThat(bookingResponses).containsKeys("201", "400", "404", "409");
        for (String failure : new String[] {"400", "404", "409"}) {
            assertThat(document.read(
                    booking + ".['" + failure + "'].content.['application/problem+json'].schema.$ref",
                    String.class))
                    .endsWith("ProblemDetail");
        }
    }
}
