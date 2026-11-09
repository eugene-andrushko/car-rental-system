package com.carrental.reservation.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationPeriodTest {

    private static final Instant JANUARY_FIRST_AT_TEN = Instant.parse("2030-01-01T10:00:00Z");

    @Test
    void givenNanosecondPickUp_whenCreate_thenTruncatedToStoredMicrosecondPrecision() {
        // given
        Instant pickUpAt = JANUARY_FIRST_AT_TEN.plusNanos(1_234_567);

        // when
        ReservationPeriod period = ReservationPeriod.ofDays(pickUpAt, 1);

        // then
        assertThat(period.pickUpAt()).isEqualTo(JANUARY_FIRST_AT_TEN.plusNanos(1_234_000));
    }

    @Test
    void givenPickUpDuringDay_whenCalculateOccupiedDays_thenReturnDayIsOccupied() {
        // given
        ReservationPeriod period = ReservationPeriod.ofDays(JANUARY_FIRST_AT_TEN, 3);

        // when
        OccupiedDays occupiedDays = period.calculateOccupiedDays();

        // then
        assertThat(occupiedDays).isEqualTo(new OccupiedDays(LocalDate.parse("2030-01-01"), LocalDate.parse("2030-01-04")));
        assertThat(occupiedDays.countDays()).isEqualTo(4);
    }

    @Test
    void givenPickUpAtMidnight_whenCalculateOccupiedDays_thenReturnDayIsNotOccupied() {
        // given
        ReservationPeriod period = ReservationPeriod.ofDays(Instant.parse("2030-01-01T00:00:00Z"), 3);

        // when
        OccupiedDays occupiedDays = period.calculateOccupiedDays();

        // then
        assertThat(occupiedDays).isEqualTo(new OccupiedDays(LocalDate.parse("2030-01-01"), LocalDate.parse("2030-01-03")));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, -1})
    void givenReturnNotAfterStoredPickUp_whenCreate_thenRejected(long nanosAfterPickUp) {
        // given
        Instant returnAt = JANUARY_FIRST_AT_TEN.plusNanos(nanosAfterPickUp);

        // when, then
        assertThatThrownBy(() -> new ReservationPeriod(JANUARY_FIRST_AT_TEN, returnAt))
                .isInstanceOf(InvalidReservationException.class);
    }

    @Test
    void givenSamePickUpWithNanoseconds_whenIsSamePeriodAs_thenSame() {
        // given
        ReservationPeriod period = ReservationPeriod.ofDays(JANUARY_FIRST_AT_TEN.plusNanos(1_234_567), 3);

        // when
        boolean samePeriod = period.isSamePeriodAs(JANUARY_FIRST_AT_TEN.plusNanos(1_234_567), 3);

        // then
        assertThat(samePeriod).isTrue();
    }

    @Test
    void givenPickUpAtLatestInstant_whenIsSamePeriodAs_thenDifferentWithoutOverflow() {
        // given
        ReservationPeriod period = ReservationPeriod.ofDays(JANUARY_FIRST_AT_TEN, 3);

        // when
        boolean samePeriod = period.isSamePeriodAs(Instant.MAX, Integer.MAX_VALUE);

        // then
        assertThat(samePeriod).isFalse();
    }
}
