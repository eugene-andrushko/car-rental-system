package com.carrental.reservation.web;

import com.carrental.reservation.application.ReservationRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

record CreateReservationBody(
        @NotBlank @Size(max = 20) String carType,
        @NotNull Instant pickUpAt,
        @Positive int days,
        @NotBlank @Size(max = 100) String customerId) {

    ReservationRequest toReservationRequest(String idempotencyKey) {
        return new ReservationRequest(carType, pickUpAt, days, customerId, idempotencyKey);
    }
}
