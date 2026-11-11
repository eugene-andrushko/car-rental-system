package com.carrental.reservation.application;


import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.carrental.FixedClockConfiguration.NOW;

public final class ReservationRequestFixtures {

    public static final Instant TOMORROW = NOW.plus(Duration.ofDays(1));
    public static final int DEFAULT_DAYS = 3;

    private ReservationRequestFixtures() {
    }

    public static ReservationRequest van() {
        return ofCarType("VAN");
    }

    public static ReservationRequest ofCarType(String carTypeName) {
        return new ReservationRequest(carTypeName, TOMORROW, DEFAULT_DAYS, generateCustomerId(), generateIdempotencyKey());
    }

    public static ReservationRequest withCarType(ReservationRequest request, String carTypeName) {
        return new ReservationRequest(carTypeName, request.pickUpAt(), request.days(), request.customerId(), request.idempotencyKey());
    }

    public static ReservationRequest withPeriod(ReservationRequest request, Instant pickUpAt, int days) {
        return new ReservationRequest(request.carTypeName(), pickUpAt, days, request.customerId(), request.idempotencyKey());
    }

    public static ReservationRequest withCustomerId(ReservationRequest request, String customerId) {
        return new ReservationRequest(request.carTypeName(), request.pickUpAt(), request.days(), customerId, request.idempotencyKey());
    }

    public static String generateCustomerId() {
        return "customer-" + UUID.randomUUID();
    }

    private static String generateIdempotencyKey() {
        return "key-" + UUID.randomUUID();
    }
}
