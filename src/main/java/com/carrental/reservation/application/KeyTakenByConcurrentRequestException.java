package com.carrental.reservation.application;

class KeyTakenByConcurrentRequestException extends RuntimeException {

    KeyTakenByConcurrentRequestException(Throwable cause) {
        super("Idempotency key was taken by a concurrent request", cause);
    }
}
