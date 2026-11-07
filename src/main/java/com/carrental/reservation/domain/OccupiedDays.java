package com.carrental.reservation.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record OccupiedDays(LocalDate firstDay, LocalDate lastDay) {

    public int countDays() {
        return (int) ChronoUnit.DAYS.between(firstDay, lastDay) + 1;
    }
}
