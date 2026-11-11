package com.carrental.reservation.application;

import com.carrental.CarRentalIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.LocalDate;
import java.util.Map;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;

@CarRentalIntegrationTest
class CarAvailabilityCalendarIntegrationTest {

    private static final LocalDate LAST_DAY_TO_OPEN = LocalDate.parse("2030-05-22");

    @Autowired
    private CarAvailabilityCalendar carAvailabilityCalendar;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void givenFixedClock_whenApplicationStarts_thenDaysUpToSpareWeekAfterLatestReturnDayAreOpenWithAllCarsAvailable() {
        // given, when
        Map<String, Integer> availableCarsOnLastDay = findAvailableCarsByTypeName(LAST_DAY_TO_OPEN);
        Map<String, Integer> availableCarsOnDayAfter = findAvailableCarsByTypeName(LAST_DAY_TO_OPEN.plusDays(1));

        // then
        assertThat(availableCarsOnLastDay).containsExactlyInAnyOrderEntriesOf(Map.of("SEDAN", 3, "SUV", 2, "VAN", 1));
        assertThat(availableCarsOnDayAfter).isEmpty();
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

    private Map<String, Integer> findAvailableCarsByTypeName(LocalDate calendarDay) {
        return jdbcClient.sql("""
                        select car_type.name, car_type.car_count - car_availability.reserved_cars
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
