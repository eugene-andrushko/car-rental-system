package com.carrental.reservation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "reservation")
record ReservationProperties(int maxDays, Duration bookingHorizon) {
}
