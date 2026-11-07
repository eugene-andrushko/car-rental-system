package com.carrental.reservation.application;

import com.carrental.reservation.domain.ReservationRules;
import com.carrental.reservation.persistence.CarAvailabilityRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Component
class CarAvailabilityCalendar {

    private final CarAvailabilityRepository carAvailabilityRepository;
    private final ReservationRules reservationRules;
    private final Clock clock;

    CarAvailabilityCalendar(CarAvailabilityRepository carAvailabilityRepository, ReservationRules reservationRules, Clock clock) {
        this.carAvailabilityRepository = carAvailabilityRepository;
        this.reservationRules = reservationRules;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 0 0 * * *", zone = "UTC")
    void openBookableDays() {
        LocalDate today = LocalDate.now(clock);
        LocalDate lastBookableDay = LocalDate.ofInstant(clock.instant()
                .plus(reservationRules.bookingHorizon())
                .plus(Duration.ofDays(reservationRules.maxDays())), ZoneOffset.UTC);
        today.datesUntil(lastBookableDay.plusDays(1)).forEach(carAvailabilityRepository::openDayIfMissing);
    }
}
