package com.carrental.reservation.domain;

import java.util.UUID;

public class ReservationAlreadyStartedException extends RuntimeException {

    public ReservationAlreadyStartedException(UUID reservationId) {
        super("Reservation has already started and can no longer be cancelled: " + reservationId);
    }
}
