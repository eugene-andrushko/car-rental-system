package com.carrental.reservation.domain;

import com.carrental.fleet.domain.CarType;

import java.time.Instant;

public record ReservationRequest(String carTypeName, Instant startsAt, int days, String customerId, String idempotencyKey) {

    public ReservationRequest {
        carTypeName = CarType.normalizeName(carTypeName);
    }

    public ReservationPeriod toReservationPeriod() {
        return ReservationPeriod.ofDays(startsAt, days);
    }
}
