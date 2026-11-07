package com.carrental.reservation.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationPeriodTest {

    private static final Instant JANUARY_FIRST_AT_TEN = Instant.parse("2030-01-01T10:00:00Z");

    @Test
    void givenNanosecondStart_whenCreate_thenTruncatedToStoredMicrosecondPrecision() {
        // given
        Instant startsAt = JANUARY_FIRST_AT_TEN.plusNanos(1_234_567);

        // when
        ReservationPeriod period = ReservationPeriod.ofDays(startsAt, 1);

        // then
        assertThat(period.startsAt()).isEqualTo(JANUARY_FIRST_AT_TEN.plusNanos(1_234_000));
    }

    @Test
    void givenStartDuringDay_whenCalculateOccupiedDays_thenReturnDayIsOccupied() {
        // given
        ReservationPeriod period = ReservationPeriod.ofDays(JANUARY_FIRST_AT_TEN, 3);

        // when
        OccupiedDays occupiedDays = period.calculateOccupiedDays();

        // then
        assertThat(occupiedDays).isEqualTo(new OccupiedDays(LocalDate.parse("2030-01-01"), LocalDate.parse("2030-01-04")));
        assertThat(occupiedDays.countDays()).isEqualTo(4);
    }

    @Test
    void givenStartAtMidnight_whenCalculateOccupiedDays_thenReturnDayIsFree() {
        // given
        ReservationPeriod period = ReservationPeriod.ofDays(Instant.parse("2030-01-01T00:00:00Z"), 3);

        // when
        OccupiedDays occupiedDays = period.calculateOccupiedDays();

        // then
        assertThat(occupiedDays).isEqualTo(new OccupiedDays(LocalDate.parse("2030-01-01"), LocalDate.parse("2030-01-03")));
    }
}
