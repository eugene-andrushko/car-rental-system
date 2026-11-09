package com.carrental.reservation.domain;

import java.util.UUID;

public class PickUpTimePassedException extends RuntimeException {

    public PickUpTimePassedException(UUID reservationId) {
        super("Pick-up time has passed, so the reservation can no longer be cancelled: " + reservationId);
    }
}
