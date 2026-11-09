package com.carrental.reservation.web;

import com.carrental.reservation.domain.Reservation;
import com.carrental.reservation.domain.ReservationStatus;

import java.time.Instant;
import java.util.UUID;

record ReservationResponse(UUID id, String carType, Instant pickUpAt, Instant returnAt, ReservationStatus status) {

    static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.id(),
                reservation.carType().name(),
                reservation.period().pickUpAt(),
                reservation.period().returnAt(),
                reservation.status());
    }
}
