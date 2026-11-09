package com.carrental.reservation.domain;

public class NoCarAvailableException extends RuntimeException {

    public NoCarAvailableException(String carTypeName, ReservationPeriod period) {
        super("No " + carTypeName + " available from " + period.pickUpAt() + " to " + period.returnAt());
    }
}
