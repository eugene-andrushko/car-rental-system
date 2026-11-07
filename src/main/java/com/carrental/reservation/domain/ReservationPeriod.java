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
        @Column(name = "starts_at", nullable = false) Instant startsAt,
        @Column(name = "ends_at", nullable = false) Instant endsAt) {

    public ReservationPeriod {
        startsAt = startsAt.truncatedTo(ChronoUnit.MICROS);
        endsAt = endsAt.truncatedTo(ChronoUnit.MICROS);
    }

    public static ReservationPeriod ofDays(Instant startsAt, int days) {
        return new ReservationPeriod(startsAt, startsAt.plus(Duration.ofDays(days)));
    }

    public boolean isStartedAt(Instant now) {
        return !startsAt.isAfter(now);
    }

    public OccupiedDays calculateOccupiedDays() {
        return new OccupiedDays(
                LocalDate.ofInstant(startsAt, ZoneOffset.UTC),
                LocalDate.ofInstant(endsAt.minusNanos(1), ZoneOffset.UTC));
    }
}
