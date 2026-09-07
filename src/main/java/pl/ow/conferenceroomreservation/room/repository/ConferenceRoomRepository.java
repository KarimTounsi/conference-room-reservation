package pl.ow.conferenceroomreservation.room.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.ow.conferenceroomreservation.room.entity.ConferenceRoom;

public interface ConferenceRoomRepository extends JpaRepository<ConferenceRoom, Long> {

    boolean existsByName(String name);
}
