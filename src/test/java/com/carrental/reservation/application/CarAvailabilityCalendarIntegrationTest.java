package com.carrental.reservation.application;

import com.carrental.reservation.domain.ReservationRules;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CarAvailabilityCalendarIntegrationTest {

    @Autowired
    private CarAvailabilityCalendar carAvailabilityCalendar;

    @Autowired
    private ReservationRules reservationRules;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void givenApplicationStarted_whenCheckLastBookableDay_thenEveryCarTypeHasAllCarsFree() {
        // given
        LocalDate lastBookableDay = LocalDate.now(ZoneOffset.UTC)
                .plusDays(reservationRules.bookingHorizon().toDays() + reservationRules.maxDays());

        // when
        Map<String, Integer> freeCarsByTypeName = findFreeCarsByTypeName(lastBookableDay);

        // then
        assertThat(freeCarsByTypeName).containsExactlyInAnyOrderEntriesOf(Map.of("SEDAN", 3, "SUV", 2, "VAN", 1));
    }

    @Test
    void givenDaysAlreadyOpen_whenOpenBookableDaysAgain_thenNoDayIsAddedTwice() {
        // given
        long openDayCount = countOpenDays();

        // when
        carAvailabilityCalendar.openBookableDays();

        // then
        assertThat(countOpenDays()).isEqualTo(openDayCount);
    }

    private Map<String, Integer> findFreeCarsByTypeName(LocalDate calendarDay) {
        return jdbcClient.sql("""
                        select car_type.name, car_availability.free_cars
                        from car_availability join car_type on car_type.id = car_availability.car_type_id
                        where car_availability.calendar_day = :calendarDay
                        """)
                .param("calendarDay", calendarDay)
                .query((row, rowNumber) -> Map.entry(row.getString(1), row.getInt(2)))
                .list().stream()
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private long countOpenDays() {
        return jdbcClient.sql("select count(*) from car_availability").query(Long.class).single();
    }
}
