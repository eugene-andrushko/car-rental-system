package com.carrental.reservation.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationRulesTest {

    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");
    private static final Duration BOOKING_HORIZON = Duration.ofDays(90);

    private final ReservationRules reservationRules = new ReservationRules(30, BOOKING_HORIZON);

    @Test
    void givenStartAndDays_whenCreatePeriod_thenPeriodEndsThatManyDaysLater() {
        // given
        Instant startsAt = Instant.parse("2030-01-05T09:30:00Z");

        // when
        ReservationPeriod period = reservationRules.validateAndCreatePeriod(startsAt, 3, NOW);

        // then
        assertThat(period).isEqualTo(new ReservationPeriod(startsAt, Instant.parse("2030-01-08T09:30:00Z")));
    }

    @Test
    void givenStartInPast_whenCreatePeriod_thenRejected() {
        // given
        Instant startsAt = NOW.minusSeconds(1);

        // when, then
        assertThatThrownBy(() -> reservationRules.validateAndCreatePeriod(startsAt, 1, NOW))
                .isInstanceOf(InvalidReservationException.class);
    }

    @Test
    void givenStartAtEndOfBookingHorizon_whenCreatePeriod_thenAccepted() {
        // given
        Instant startsAt = NOW.plus(BOOKING_HORIZON);

        // when
        ReservationPeriod period = reservationRules.validateAndCreatePeriod(startsAt, 1, NOW);

        // then
        assertThat(period.startsAt()).isEqualTo(startsAt);
    }

    @Test
    void givenStartBeyondBookingHorizon_whenCreatePeriod_thenRejected() {
        // given
        Instant startsAt = NOW.plus(BOOKING_HORIZON).plusSeconds(1);

        // when, then
        assertThatThrownBy(() -> reservationRules.validateAndCreatePeriod(startsAt, 1, NOW))
                .isInstanceOf(InvalidReservationException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 31})
    void givenDaysOutsideAllowedRange_whenCreatePeriod_thenRejected(int days) {
        // given
        Instant startsAt = NOW;

        // when, then
        assertThatThrownBy(() -> reservationRules.validateAndCreatePeriod(startsAt, days, NOW))
                .isInstanceOf(InvalidReservationException.class);
    }
}
