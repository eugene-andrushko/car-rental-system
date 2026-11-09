package com.carrental.reservation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Embeddable
public record ReservationPeriod(
        @Column(name = "pick_up_at", nullable = false) Instant pickUpAt,
        @Column(name = "return_at", nullable = false) Instant returnAt) {

    public ReservationPeriod {
        pickUpAt = toStoredPrecision(pickUpAt);
        returnAt = toStoredPrecision(returnAt);
        if (!pickUpAt.isBefore(returnAt)) {
            throw new InvalidReservationException("Return must be after pick-up: " + pickUpAt + " to " + returnAt);
        }
    }

    public static ReservationPeriod ofDays(Instant pickUpAt, int days) {
        return new ReservationPeriod(pickUpAt, pickUpAt.plus(Duration.ofDays(days)));
    }

    public boolean isSamePeriodAs(Instant requestedPickUpAt, int requestedDays) {
        return pickUpAt.equals(toStoredPrecision(requestedPickUpAt))
                && Duration.between(pickUpAt, returnAt).equals(Duration.ofDays(requestedDays));
    }

    public boolean isPickUpTimeReachedBy(Instant now) {
        return !pickUpAt.isAfter(now);
    }

    public OccupiedDays calculateOccupiedDays() {
        return new OccupiedDays(
                LocalDate.ofInstant(pickUpAt, ZoneOffset.UTC),
                LocalDate.ofInstant(returnAt.minusNanos(1), ZoneOffset.UTC));
    }

    private static Instant toStoredPrecision(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS);
    }
}
