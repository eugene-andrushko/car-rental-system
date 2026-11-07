package com.carrental.reservation.application;

import com.carrental.reservation.domain.ReservationRules;
import com.carrental.reservation.persistence.CarAvailabilityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Component
class CarAvailabilityCalendar implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(CarAvailabilityCalendar.class);

    private final CarAvailabilityRepository carAvailabilityRepository;
    private final ReservationRules reservationRules;
    private final Clock clock;

    CarAvailabilityCalendar(CarAvailabilityRepository carAvailabilityRepository, ReservationRules reservationRules, Clock clock) {
        this.carAvailabilityRepository = carAvailabilityRepository;
        this.reservationRules = reservationRules;
        this.clock = clock;
    }

    @Override
    public void afterSingletonsInstantiated() {
        openBookableDays();
    }

    @Scheduled(cron = "${reservation.calendar-cron}", zone = "UTC")
    void openBookableDays() {
        LocalDate today = LocalDate.now(clock);
        LocalDate lastBookableDayTomorrow = LocalDate.ofInstant(clock.instant()
                .plus(reservationRules.bookingHorizon())
                .plus(Duration.ofDays(reservationRules.maxDays() + 1)), ZoneOffset.UTC);
        today.datesUntil(lastBookableDayTomorrow.plusDays(1)).forEach(carAvailabilityRepository::openDayIfMissing);
        log.info("Opened bookable days from {} to {}", today, lastBookableDayTomorrow);
    }
}
