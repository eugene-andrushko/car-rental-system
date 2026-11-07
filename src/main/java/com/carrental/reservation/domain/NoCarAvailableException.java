package com.carrental.reservation.domain;

public class NoCarAvailableException extends RuntimeException {

    public NoCarAvailableException(String carTypeName, ReservationPeriod period) {
        super("No " + carTypeName + " available from " + period.startsAt() + " to " + period.endsAt());
    }
}
