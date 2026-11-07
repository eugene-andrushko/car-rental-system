package com.carrental.reservation.persistence;

import com.carrental.reservation.domain.OccupiedDays;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public class CarAvailabilityRepository {

    private final JdbcClient jdbcClient;

    CarAvailabilityRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public int reserveCarOnEachDay(long carTypeId, OccupiedDays occupiedDays) {
        return jdbcClient.sql("""
                        update car_availability
                        set free_cars = free_cars - 1
                        where car_type_id = :carTypeId
                          and calendar_day between :firstDay and :lastDay
                          and free_cars > 0
                        """)
                .param("carTypeId", carTypeId)
                .param("firstDay", occupiedDays.firstDay())
                .param("lastDay", occupiedDays.lastDay())
                .update();
    }

    public void releaseCarOnEachDay(long carTypeId, OccupiedDays occupiedDays) {
        jdbcClient.sql("""
                        update car_availability
                        set free_cars = free_cars + 1
                        where car_type_id = :carTypeId
                          and calendar_day between :firstDay and :lastDay
                        """)
                .param("carTypeId", carTypeId)
                .param("firstDay", occupiedDays.firstDay())
                .param("lastDay", occupiedDays.lastDay())
                .update();
    }

    public void openDayIfMissing(LocalDate calendarDay) {
        try {
            jdbcClient.sql("""
                            insert into car_availability (car_type_id, calendar_day, free_cars)
                            select car_type.id, cast(:calendarDay as date), car_type.car_count
                            from car_type
                            where not exists (
                                select 1 from car_availability
                                where car_availability.car_type_id = car_type.id
                                  and car_availability.calendar_day = cast(:calendarDay as date))
                            """)
                    .param("calendarDay", calendarDay)
                    .update();
        } catch (DuplicateKeyException dayOpenedByAnotherInstance) {
            return;
        }
    }
}
