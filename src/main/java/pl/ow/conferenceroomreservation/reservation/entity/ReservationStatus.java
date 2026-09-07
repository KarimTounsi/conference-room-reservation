package pl.ow.conferenceroomreservation.reservation.entity;

public enum ReservationStatus {

    /** Holds the slot; only active reservations take part in overlap detection. */
    ACTIVE,

    /** Cancelled reservations stay as an audit trail and release the slot. */
    CANCELLED
}
