package pl.ow.conferenceroomreservation.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.ow.conferenceroomreservation.common.time.InvalidTimeRangeException;
import pl.ow.conferenceroomreservation.common.time.TimeRange;
import pl.ow.conferenceroomreservation.reservation.dto.CreateReservationRequest;
import pl.ow.conferenceroomreservation.reservation.dto.ReservationResponse;
import pl.ow.conferenceroomreservation.reservation.entity.Reservation;
import pl.ow.conferenceroomreservation.reservation.entity.ReservationStatus;
import pl.ow.conferenceroomreservation.reservation.exception.ReservationNotFoundException;
import pl.ow.conferenceroomreservation.reservation.exception.ReservationOverlapException;
import pl.ow.conferenceroomreservation.reservation.mapper.ReservationMapper;
import pl.ow.conferenceroomreservation.reservation.repository.ReservationRepository;
import pl.ow.conferenceroomreservation.room.entity.ConferenceRoom;
import pl.ow.conferenceroomreservation.room.exception.ConferenceRoomNotFoundException;
import pl.ow.conferenceroomreservation.room.repository.ConferenceRoomRepository;

@ExtendWith(MockitoExtension.class)
class ReservationServiceImplTest {

    private static final OffsetDateTime NINE = OffsetDateTime.parse("2026-09-10T09:00:00+02:00");
    private static final OffsetDateTime TEN = OffsetDateTime.parse("2026-09-10T10:00:00+02:00");

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ConferenceRoomRepository conferenceRoomRepository;

    @Spy
    private ReservationMapper reservationMapper = new ReservationMapper();

    @InjectMocks
    private ReservationServiceImpl service;

    private final ConferenceRoom room = ConferenceRoom.create("Alpha", 10);

    private CreateReservationRequest request() {
        return new CreateReservationRequest("Karim", NINE, TEN);
    }

    @Test
    void shouldRejectBookingWhenRoomDoesNotExist() {
        given(conferenceRoomRepository.findById(404L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.createReservation(404L, request()))
                .isInstanceOf(ConferenceRoomNotFoundException.class);

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void shouldRejectOverlappingBookingAndSaveNothing() {
        given(conferenceRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(reservationRepository.existsActiveOverlapping(eq(1L), any(TimeRange.class))).willReturn(true);

        assertThatThrownBy(() -> service.createReservation(1L, request()))
                .isInstanceOf(ReservationOverlapException.class)
                .hasMessageContaining("already booked");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void shouldStoreBookingAsActive() {
        given(conferenceRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(reservationRepository.existsActiveOverlapping(eq(1L), any(TimeRange.class))).willReturn(false);
        given(reservationRepository.save(any())).willAnswer(call -> call.getArgument(0));

        ReservationResponse created = service.createReservation(1L, request());

        assertThat(created.status()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(created.bookedBy()).isEqualTo("Karim");
        assertThat(created.startTime()).isEqualTo(NINE);

        ArgumentCaptor<Reservation> saved = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(ReservationStatus.ACTIVE);
    }


    @Test
    void shouldCancelActiveReservation() {
        Reservation reservation = Reservation.create(room, "Karim", TimeRange.of(NINE, TEN));
        given(reservationRepository.findById(5L)).willReturn(Optional.of(reservation));

        service.cancelReservation(5L);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }


    @Test
    void shouldRejectCancellingUnknownReservation() {
        given(reservationRepository.findById(404L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelReservation(404L))
                .isInstanceOf(ReservationNotFoundException.class);
    }

    @Test
    void shouldRejectListingForUnknownRoom() {
        given(conferenceRoomRepository.existsById(404L)).willReturn(false);

        assertThatThrownBy(() ->
                service.findReservations(404L, null, null, null))
                .isInstanceOf(ConferenceRoomNotFoundException.class);
    }

    @Test
    void shouldRejectWindowWhoseEndIsNotAfterStart() {
        given(conferenceRoomRepository.existsById(1L)).willReturn(true);

        assertThatThrownBy(() ->
                service.findReservations(1L, TEN, NINE, null))
                .isInstanceOf(InvalidTimeRangeException.class)
                .hasMessageContaining("to must be after from");
    }
}
