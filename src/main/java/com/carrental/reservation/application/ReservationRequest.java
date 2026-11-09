package com.carrental.reservation.application;

import com.carrental.fleet.domain.CarType;

import java.time.Instant;
import java.util.Objects;

public record ReservationRequest(String carTypeName, Instant pickUpAt, int days, String customerId, String idempotencyKey) {

    public ReservationRequest {
        carTypeName = CarType.normalizeName(Objects.requireNonNull(carTypeName, "carTypeName"));
    }
}
