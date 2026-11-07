package com.carrental.reservation.application;


import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.carrental.FixedClockConfiguration.NOW;

final class ReservationRequestFixtures {

    static final Instant TOMORROW = NOW.plus(Duration.ofDays(1));
    static final int DEFAULT_DAYS = 3;

    private ReservationRequestFixtures() {
    }

    static ReservationRequest van() {
        return ofCarType("VAN");
    }

    static ReservationRequest ofCarType(String carTypeName) {
        return new ReservationRequest(carTypeName, TOMORROW, DEFAULT_DAYS, generateCustomerId(), generateIdempotencyKey());
    }

    static ReservationRequest withCarType(ReservationRequest request, String carTypeName) {
        return new ReservationRequest(carTypeName, request.startsAt(), request.days(), request.customerId(), request.idempotencyKey());
    }

    static ReservationRequest withPeriod(ReservationRequest request, Instant startsAt, int days) {
        return new ReservationRequest(request.carTypeName(), startsAt, days, request.customerId(), request.idempotencyKey());
    }

    static ReservationRequest withCustomerId(ReservationRequest request, String customerId) {
        return new ReservationRequest(request.carTypeName(), request.startsAt(), request.days(), customerId, request.idempotencyKey());
    }

    static String generateCustomerId() {
        return "customer-" + UUID.randomUUID();
    }

    private static String generateIdempotencyKey() {
        return "key-" + UUID.randomUUID();
    }
}
