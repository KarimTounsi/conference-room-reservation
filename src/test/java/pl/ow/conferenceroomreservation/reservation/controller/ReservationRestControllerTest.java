package pl.ow.conferenceroomreservation.reservation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.ow.conferenceroomreservation.common.time.TimeRange;
import pl.ow.conferenceroomreservation.reservation.dto.ReservationResponse;
import pl.ow.conferenceroomreservation.reservation.entity.ReservationStatus;
import pl.ow.conferenceroomreservation.reservation.exception.ReservationOverlapException;
import pl.ow.conferenceroomreservation.reservation.service.ReservationService;

@WebMvcTest(ReservationRestController.class)
class ReservationRestControllerTest {

    private static final OffsetDateTime NINE = OffsetDateTime.parse("2026-09-10T09:00:00+02:00");
    private static final OffsetDateTime TEN = OffsetDateTime.parse("2026-09-10T10:00:00+02:00");

    private static String body(String bookedBy, String start, String end) {
        return "{\"bookedBy\":\"" + bookedBy + "\",\"startTime\":\"" + start
                + "\",\"endTime\":\"" + end + "\"}";
    }

    private static final String VALID_BODY =
            body("Karim", "2026-09-10T09:00:00+02:00", "2026-09-10T10:00:00+02:00");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService reservationService;

    @Test
    void shouldCreateReservationAndReturnLocation() throws Exception {
        given(reservationService.createReservation(eq(1L), any())).willReturn(
                new ReservationResponse(7L, 1L, "Karim", NINE, TEN, ReservationStatus.ACTIVE));

        mockMvc.perform(post("/api/v1/rooms/1/reservations")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/reservations/7"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.roomId").value(1))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void shouldReturnConflictWhenReservationOverlaps() throws Exception {
        willThrow(new ReservationOverlapException(1L, TimeRange.of(NINE, TEN)))
                .given(reservationService).createReservation(eq(1L), any());

        mockMvc.perform(post("/api/v1/rooms/1/reservations")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/reservation-overlap"))
                .andExpect(jsonPath("$.title").value("Reservation time conflict"))
                .andExpect(jsonPath("$.detail").value(Matchers.containsString("already booked")));
    }


    @Test
    void shouldRejectEndTimeNotAfterStartTime() throws Exception {
        mockMvc.perform(post("/api/v1/rooms/1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Karim", "2026-09-10T10:00:00+02:00", "2026-09-10T09:00:00+02:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-failed"))
                .andExpect(jsonPath("$.errors[0].field").value("endTime"))
                .andExpect(jsonPath("$.errors[0].message").value("endTime must be after startTime"));
    }






    @Test
    void shouldReturnNoContentWhenCancelling() throws Exception {
        mockMvc.perform(delete("/api/v1/reservations/7"))
                .andExpect(status().isNoContent());

        verify(reservationService).cancelReservation(7L);
    }

}
