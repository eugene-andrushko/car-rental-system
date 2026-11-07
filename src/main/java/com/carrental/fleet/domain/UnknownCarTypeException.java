package com.carrental.fleet.domain;

public class UnknownCarTypeException extends RuntimeException {

    public UnknownCarTypeException(String carTypeName) {
        super("Unknown car type: " + carTypeName);
    }
}
