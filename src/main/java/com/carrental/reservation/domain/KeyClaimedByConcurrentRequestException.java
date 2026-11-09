package com.carrental.reservation.domain;

public class KeyClaimedByConcurrentRequestException extends RuntimeException {

    public KeyClaimedByConcurrentRequestException(String idempotencyKey, Throwable cause) {
        super("Another request with the same Idempotency-Key was processed at the same time, retry to get its result: "
                + idempotencyKey, cause);
    }
}
