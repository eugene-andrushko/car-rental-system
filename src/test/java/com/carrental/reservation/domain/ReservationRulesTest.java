package com.carrental.reservation.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationRulesTest {

    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");
    private static final Duration BOOKING_HORIZON = Duration.ofDays(90);

    private final ReservationRules reservationRules = new ReservationRules(30, BOOKING_HORIZON);

    @Test
    void givenPickUpAndDays_whenValidateAndCreatePeriod_thenReturnIsThatManyDaysLater() {
        // given
        Instant pickUpAt = Instant.parse("2030-01-05T09:30:00Z");

        // when
        ReservationPeriod period = reservationRules.validateAndCreatePeriod(pickUpAt, 3, NOW);

        // then
        assertThat(period).isEqualTo(new ReservationPeriod(pickUpAt, Instant.parse("2030-01-08T09:30:00Z")));
    }

    @Test
    void givenPickUpInPast_whenValidateAndCreatePeriod_thenRejected() {
        // given
        Instant pickUpAt = NOW.minusSeconds(1);

        // when, then
        assertThatThrownBy(() -> reservationRules.validateAndCreatePeriod(pickUpAt, 1, NOW))
                .isInstanceOf(InvalidReservationException.class);
    }

    @Test
    void givenPickUpAtEndOfBookingHorizon_whenValidateAndCreatePeriod_thenAccepted() {
        // given
        Instant pickUpAt = NOW.plus(BOOKING_HORIZON);

        // when
        ReservationPeriod period = reservationRules.validateAndCreatePeriod(pickUpAt, 1, NOW);

        // then
        assertThat(period.pickUpAt()).isEqualTo(pickUpAt);
    }

    @Test
    void givenPickUpBeyondBookingHorizon_whenValidateAndCreatePeriod_thenRejected() {
        // given
        Instant pickUpAt = NOW.plus(BOOKING_HORIZON).plusSeconds(1);

        // when, then
        assertThatThrownBy(() -> reservationRules.validateAndCreatePeriod(pickUpAt, 1, NOW))
                .isInstanceOf(InvalidReservationException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 31})
    void givenDaysOutsideAllowedRange_whenValidateAndCreatePeriod_thenRejected(int days) {
        // given
        Instant pickUpAt = NOW;

        // when, then
        assertThatThrownBy(() -> reservationRules.validateAndCreatePeriod(pickUpAt, days, NOW))
                .isInstanceOf(InvalidReservationException.class);
    }

    @Test
    void givenNow_whenCalculateLatestReturnDay_thenReturnDayOfLongestReservationPickedUpAtHorizonEnd() {
        // given, when
        LocalDate latestReturnDay = reservationRules.calculateLatestReturnDay(NOW);

        // then
        assertThat(latestReturnDay).isEqualTo(LocalDate.parse("2030-05-01"));
    }
}
