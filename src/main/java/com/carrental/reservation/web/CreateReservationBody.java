package com.carrental.reservation.web;

import com.carrental.reservation.application.ReservationRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

record CreateReservationBody(
        @NotBlank String carType,
        @NotNull Instant startsAt,
        @Positive int days,
        @NotBlank String customerId) {

    ReservationRequest toReservationRequest(String idempotencyKey) {
        return new ReservationRequest(carType, startsAt, days, customerId, idempotencyKey);
    }
}
