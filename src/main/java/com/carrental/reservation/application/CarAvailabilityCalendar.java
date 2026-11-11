package com.carrental.reservation.application;

import com.carrental.fleet.persistence.CarAvailabilityRepository;
import com.carrental.reservation.domain.ReservationRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Component
class CarAvailabilityCalendar implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(CarAvailabilityCalendar.class);
    // Days opened beyond the latest return day, so a missed run, or runs up to a week apart, leave no bookable day closed.
    private static final int SPARE_DAYS_OPENED = 7;

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
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        LocalDate lastDayToOpen = reservationRules.calculateLatestReturnDay(now).plusDays(SPARE_DAYS_OPENED);
        today.datesUntil(lastDayToOpen.plusDays(1)).forEach(carAvailabilityRepository::openDayForEveryCarType);
        log.info("Opened bookable days from {} to {}", today, lastDayToOpen);
    }
}
