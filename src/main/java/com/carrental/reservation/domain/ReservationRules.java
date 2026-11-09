package com.carrental.reservation.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

public record ReservationRules(int maxDays, Duration bookingHorizon) {

    public ReservationPeriod validateAndCreatePeriod(Instant pickUpAt, int days, Instant now) {
        if (pickUpAt.isBefore(now)) {
            throw new InvalidReservationException("Pick-up must not be in the past: " + pickUpAt);
        }
        if (pickUpAt.isAfter(now.plus(bookingHorizon))) {
            throw new InvalidReservationException("Pick-up must be within " + bookingHorizon.toDays() + " days: " + pickUpAt);
        }
        if (days < 1 || days > maxDays) {
            throw new InvalidReservationException("Days must be between 1 and " + maxDays + ": " + days);
        }
        return ReservationPeriod.ofDays(pickUpAt, days);
    }

    public LocalDate calculateLatestReturnDay(Instant now) {
        return ReservationPeriod.ofDays(now.plus(bookingHorizon), maxDays).calculateOccupiedDays().lastDay();
    }
}
