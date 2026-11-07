package com.carrental.reservation.domain;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.Instant;

@ConfigurationProperties(prefix = "reservation")
public record ReservationRules(int maxDays, Duration bookingHorizon) {

    public ReservationPeriod validateAndCreatePeriod(Instant startsAt, int days, Instant now) {
        if (startsAt.isBefore(now)) {
            throw new InvalidReservationException("Start must not be in the past: " + startsAt);
        }
        if (startsAt.isAfter(now.plus(bookingHorizon))) {
            throw new InvalidReservationException("Start must be within " + bookingHorizon.toDays() + " days: " + startsAt);
        }
        if (days < 1 || days > maxDays) {
            throw new InvalidReservationException("Days must be between 1 and " + maxDays + ": " + days);
        }
        return ReservationPeriod.ofDays(startsAt, days);
    }
}
