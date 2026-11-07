package com.carrental.reservation.config;

import com.carrental.reservation.domain.ReservationRules;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ReservationProperties.class)
class ReservationRulesConfiguration {

    @Bean
    ReservationRules reservationRules(ReservationProperties reservationProperties) {
        return new ReservationRules(reservationProperties.maxDays(), reservationProperties.bookingHorizon());
    }
}
