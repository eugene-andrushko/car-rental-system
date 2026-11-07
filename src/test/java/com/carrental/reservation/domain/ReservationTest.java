package com.carrental.reservation.domain;

import com.carrental.fleet.domain.CarType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationTest {

    private static final Instant START = Instant.parse("2030-01-01T10:00:00Z");
    private static final CarType VAN = new CarType("VAN", 1);
    private static final ReservationPeriod PERIOD = ReservationPeriod.ofDays(START, 3);

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void givenBlankCustomerId_whenCreate_thenRejected(String customerId) {
        // given, when, then
        assertThatThrownBy(() -> Reservation.create(VAN, customerId, "key-1", PERIOD))
                .isInstanceOf(InvalidReservationException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void givenBlankIdempotencyKey_whenCreate_thenRejected(String idempotencyKey) {
        // given, when, then
        assertThatThrownBy(() -> Reservation.create(VAN, "customer-1", idempotencyKey, PERIOD))
                .isInstanceOf(InvalidReservationException.class);
    }

    @Test
    void givenReservation_whenCompareWithSameTypeAndPeriod_thenSameCarTypeAndPeriod() {
        // given
        Reservation reservation = Reservation.create(VAN, "customer-1", "key-1", PERIOD);
        ReservationPeriod samePeriod = ReservationPeriod.ofDays(START, 3);

        // when
        boolean sameCarTypeAndPeriod = reservation.isForSameCarTypeAndPeriod("VAN", samePeriod);

        // then
        assertThat(sameCarTypeAndPeriod).isTrue();
    }

    @Test
    void givenReservation_whenCompareWithDifferentDays_thenDifferentPeriod() {
        // given
        Reservation reservation = Reservation.create(VAN, "customer-1", "key-1", PERIOD);
        ReservationPeriod longerPeriod = ReservationPeriod.ofDays(START, 4);

        // when
        boolean sameCarTypeAndPeriod = reservation.isForSameCarTypeAndPeriod("VAN", longerPeriod);

        // then
        assertThat(sameCarTypeAndPeriod).isFalse();
    }
}
