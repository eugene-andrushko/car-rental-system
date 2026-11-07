package com.carrental.reservation.domain;

public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException(String idempotencyKey) {
        super("Idempotency key was already used for a different request: " + idempotencyKey);
    }
}
