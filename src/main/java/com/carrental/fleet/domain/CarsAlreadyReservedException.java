package com.carrental.fleet.domain;

public class CarsAlreadyReservedException extends RuntimeException {

    public CarsAlreadyReservedException(String carTypeName, int newCarCount) {
        super("Cannot change " + carTypeName + " to " + newCarCount
                + " cars: more cars are already reserved on at least one day");
    }
}
