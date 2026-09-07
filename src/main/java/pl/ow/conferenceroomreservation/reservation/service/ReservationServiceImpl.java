package pl.ow.conferenceroomreservation.reservation.service;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import pl.ow.conferenceroomreservation.reservation.repository.ReservationSpecifications;
import pl.ow.conferenceroomreservation.room.entity.ConferenceRoom;
import pl.ow.conferenceroomreservation.room.exception.ConferenceRoomNotFoundException;
import pl.ow.conferenceroomreservation.room.repository.ConferenceRoomRepository;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationServiceImpl implements ReservationService {

    private final ReservationRepository reservationRepository;
    private final ConferenceRoomRepository conferenceRoomRepository;
    private final ReservationMapper reservationMapper;

    @Override
    @Transactional
    public ReservationResponse createReservation(Long roomId, CreateReservationRequest request) {
        ConferenceRoom room = conferenceRoomRepository.findById(roomId)
                .orElseThrow(() -> new ConferenceRoomNotFoundException(roomId));

        // Enforced independently of bean validation, so the rule also holds for callers that never
        // went through the web layer.
        TimeRange requested = TimeRange.of(request.startTime(), request.endTime());

        // Friendly rejection for the overwhelmingly common case. Two simultaneous requests can both
        // pass this check, which is exactly what the database exclusion constraint is there for.
        if (reservationRepository.existsActiveOverlapping(roomId, requested)) {
            log.warn("Reservation rejected roomId={} reason=overlap range=[{}, {})",
                    roomId, requested.start(), requested.end());
            throw new ReservationOverlapException(roomId, requested);
        }

        Reservation saved = reservationRepository
                .save(Reservation.create(room, request.bookedBy(), requested));
        log.info("Reservation created id={} roomId={} range=[{}, {})",
                saved.getId(), roomId, requested.start(), requested.end());
        return reservationMapper.toResponse(saved);
    }

    @Override
    public List<ReservationResponse> findReservations(Long roomId, OffsetDateTime from, OffsetDateTime to,
            ReservationStatus status) {
        if (!conferenceRoomRepository.existsById(roomId)) {
            throw new ConferenceRoomNotFoundException(roomId);
        }
        if (from != null && to != null && !to.isAfter(from)) {
            // An empty or inverted window is a client mistake, same as for a booking. Deliberately
            // not a TimeRange: these bounds are only compared, never stored, so the microsecond
            // rule a stored range obeys would reject a perfectly answerable query - and explain it
            // with a message about persistence.
            throw InvalidTimeRangeException.forFilterWindow(from, to);
        }
        Specification<Reservation> specification = ReservationSpecifications.forRoom(roomId)
                .and(ReservationSpecifications.withStatus(status))
                .and(ReservationSpecifications.intersecting(from, to));

        // Ordered here rather than by the caller: a listing with no ORDER BY comes back in whatever
        // order the planner chose. Mapping happens inside the transaction because open-in-view is off.
        return reservationRepository.findAll(specification, Sort.by(Sort.Direction.ASC, "startTime"))
                .stream()
                .map(reservationMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void cancelReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        // No save() call: the entity is managed, so dirty checking issues the UPDATE - guarded by
        // the version column in the same statement.
        reservation.cancel();
        log.info("Reservation cancelled id={}", reservationId);
    }
}
