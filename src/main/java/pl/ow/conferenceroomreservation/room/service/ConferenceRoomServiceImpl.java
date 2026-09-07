package pl.ow.conferenceroomreservation.room.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.ow.conferenceroomreservation.room.dto.ConferenceRoomResponse;
import pl.ow.conferenceroomreservation.room.dto.CreateConferenceRoomRequest;
import pl.ow.conferenceroomreservation.room.entity.ConferenceRoom;
import pl.ow.conferenceroomreservation.room.exception.ConferenceRoomNameAlreadyExistsException;
import pl.ow.conferenceroomreservation.room.exception.ConferenceRoomNotFoundException;
import pl.ow.conferenceroomreservation.room.mapper.ConferenceRoomMapper;
import pl.ow.conferenceroomreservation.room.repository.ConferenceRoomRepository;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConferenceRoomServiceImpl implements ConferenceRoomService {

    private final ConferenceRoomRepository conferenceRoomRepository;
    private final ConferenceRoomMapper conferenceRoomMapper;

    @Override
    @Transactional
    public ConferenceRoomResponse createRoom(CreateConferenceRoomRequest request) {
        // Friendly path for the common case; the unique constraint still guards a concurrent
        // duplicate, and the handler translates it to the same 409.
        if (conferenceRoomRepository.existsByName(request.name())) {
            throw new ConferenceRoomNameAlreadyExistsException(request.name());
        }
        ConferenceRoom saved = conferenceRoomRepository
                .save(ConferenceRoom.create(request.name(), request.maxOccupancy()));
        log.info("Conference room created id={} maxOccupancy={}", saved.getId(), saved.getMaxOccupancy());
        return conferenceRoomMapper.toResponse(saved);
    }

    @Override
    public List<ConferenceRoomResponse> findRooms() {
        // Ordered here rather than by the caller: a listing with no ORDER BY comes back in whatever
        // order the planner chose, which makes the response unstable between identical requests.
        return conferenceRoomRepository.findAll(Sort.by(Sort.Direction.ASC, "name"))
                .stream()
                .map(conferenceRoomMapper::toResponse)
                .toList();
    }

    @Override
    public ConferenceRoomResponse findRoom(Long roomId) {
        return conferenceRoomRepository.findById(roomId)
                .map(conferenceRoomMapper::toResponse)
                .orElseThrow(() -> new ConferenceRoomNotFoundException(roomId));
    }
}
