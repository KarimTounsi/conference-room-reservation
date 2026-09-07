package pl.ow.conferenceroomreservation.reservation.repository;

import java.time.OffsetDateTime;
import org.springframework.data.jpa.domain.Specification;
import pl.ow.conferenceroomreservation.reservation.entity.Reservation;
import pl.ow.conferenceroomreservation.reservation.entity.ReservationStatus;

/**
 * Composable filters for listing reservations.
 *
 * <p>Specifications rather than a derived query per filter combination: with three optional filters
 * that would be eight methods, and the {@code :param is null} trick misfires on PostgreSQL, which
 * cannot infer the type of a bare null parameter.
 */
public final class ReservationSpecifications {

    private ReservationSpecifications() {
    }

    public static Specification<Reservation> forRoom(Long roomId) {
        return (root, query, builder) -> builder.equal(root.get("room").get("id"), roomId);
    }

    /**
     * An absent status matches everything. Returns a neutral predicate rather than {@code null},
     * because {@code Specification.and(null)} is rejected outright.
     */
    public static Specification<Reservation> withStatus(ReservationStatus status) {
        return status == null
                ? (root, query, builder) -> builder.conjunction()
                : (root, query, builder) -> builder.equal(root.get("status"), status);
    }

    /**
     * Reservations intersecting the half-open window {@code [from, to)} - the same overlap
     * condition used everywhere else. Either bound may be omitted.
     */
    public static Specification<Reservation> intersecting(OffsetDateTime from, OffsetDateTime to) {
        return (root, query, builder) -> {
            var predicate = builder.conjunction();
            if (to != null) {
                predicate = builder.and(predicate, builder.lessThan(root.get("startTime"), to));
            }
            if (from != null) {
                predicate = builder.and(predicate, builder.greaterThan(root.get("endTime"), from));
            }
            return predicate;
        };
    }
}
