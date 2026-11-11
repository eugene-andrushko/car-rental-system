package com.carrental.reservation.web;

import com.carrental.fleet.domain.CarType;

record CarTypeResponse(String name, int carCount) {

    static CarTypeResponse from(CarType carType) {
        return new CarTypeResponse(carType.name(), carType.carCount());
    }
}
