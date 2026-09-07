package pl.ow.conferenceroomreservation.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import pl.ow.conferenceroomreservation.TestcontainersConfiguration;
import pl.ow.conferenceroomreservation.reservation.dto.CreateReservationRequest;
import pl.ow.conferenceroomreservation.reservation.dto.ReservationResponse;
import pl.ow.conferenceroomreservation.room.dto.ConferenceRoomResponse;
import pl.ow.conferenceroomreservation.room.dto.CreateConferenceRoomRequest;

/**
 * Proves the concurrency design from the plan, end to end.
 *
 * <p>Scenario one shows the invariant holds under a real race: the application check alone cannot
 * stop it, because all twenty transactions can pass the check before any of them inserts. What
 * stops it is the database exclusion constraint.
 *
 * <p>Scenario two guards against over-correcting. Serialising the whole room - by bumping a version
 * on the room row, for instance - would also pass scenario one while wrongly rejecting bookings
 * that never overlapped. This test would fail if anyone did that.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class ConcurrentReservationIntegrationTest {

    private static final int THREADS = 20;
    private static final OffsetDateTime BASE = OffsetDateTime.parse("2026-09-10T09:00:00+02:00");

    @Autowired
    private TestRestTemplate restTemplate;

    private Long createRoom(String name) {
        return restTemplate.postForEntity("/api/v1/rooms",
                new CreateConferenceRoomRequest(name, 10), ConferenceRoomResponse.class).getBody().id();
    }

    /** Fires every request from its own thread, released together so they truly collide. */
    private List<HttpStatusCode> bookInParallel(Long roomId, List<OffsetDateTime> starts)
            throws Exception {
        CountDownLatch startSignal = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(starts.size())) {
            List<Callable<HttpStatusCode>> calls = starts.stream()
                    .map(start -> (Callable<HttpStatusCode>) () -> {
                        startSignal.await();
                        ResponseEntity<String> response = restTemplate.postForEntity(
                                "/api/v1/rooms/" + roomId + "/reservations",
                                new CreateReservationRequest("Karim", start, start.plusHours(1)),
                                String.class);
                        return response.getStatusCode();
                    })
                    .toList();

            List<Future<HttpStatusCode>> futures = calls.stream().map(pool::submit).toList();
            startSignal.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

            return futures.stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }).toList();
        }
    }

    private long activeReservationCount(Long roomId) {
        ResponseEntity<ReservationResponse[]> response = restTemplate.getForEntity(
                "/api/v1/rooms/" + roomId + "/reservations?status=ACTIVE", ReservationResponse[].class);
        return response.getBody().length;
    }

    @Test
    void shouldLetExactlyOneOfTwentySimultaneousRequestsWinTheSameSlot() throws Exception {
        Long roomId = createRoom("Race " + System.nanoTime());
        List<OffsetDateTime> sameSlot = java.util.Collections.nCopies(THREADS, BASE);

        List<HttpStatusCode> statuses = bookInParallel(roomId, sameSlot);

        assertThat(statuses.stream().filter(HttpStatus.CREATED::equals).count())
                .as("exactly one request may create the reservation")
                .isEqualTo(1);
        assertThat(statuses.stream().filter(HttpStatus.CONFLICT::equals).count())
                .as("every other request must be told it is a conflict, not fail some other way")
                .isEqualTo(THREADS - 1L);
        assertThat(activeReservationCount(roomId))
                .as("the database must hold exactly one active reservation")
                .isEqualTo(1);
    }

    @Test
    void shouldAcceptAllTwentySimultaneousRequestsForDisjointSlots() throws Exception {
        Long roomId = createRoom("No False Conflicts " + System.nanoTime());
        List<OffsetDateTime> disjointSlots = java.util.stream.IntStream.range(0, THREADS)
                .mapToObj(hour -> BASE.plusHours(hour))
                .toList();

        List<HttpStatusCode> statuses = bookInParallel(roomId, disjointSlots);

        assertThat(statuses).allSatisfy(status -> assertThat(status).isEqualTo(HttpStatus.CREATED));
        assertThat(activeReservationCount(roomId)).isEqualTo(THREADS);
    }

}
