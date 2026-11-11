package com.carrental.reservation.persistence;

import com.carrental.reservation.domain.OccupiedDays;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Repository
public class CarAvailabilityRepository {

    private final JdbcClient jdbcClient;

    CarAvailabilityRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public int reserveCarOnEachAvailableDay(long carTypeId, OccupiedDays occupiedDays) {
        lockDaysInOrder(carTypeId, occupiedDays);
        return jdbcClient.sql("""
                        update car_availability
                        set reserved_cars = reserved_cars + 1
                        where car_type_id = :carTypeId
                          and calendar_day between :firstDay and :lastDay
                          and reserved_cars < (select car_count from car_type where car_type.id = car_availability.car_type_id)
                        """)
                .paramSource(DaysOfCarType.of(carTypeId, occupiedDays))
                .update();
    }

    public void releaseCarOnEachDay(long carTypeId, OccupiedDays occupiedDays) {
        lockDaysInOrder(carTypeId, occupiedDays);
        jdbcClient.sql("""
                        update car_availability
                        set reserved_cars = reserved_cars - 1
                        where car_type_id = :carTypeId
                          and calendar_day between :firstDay and :lastDay
                        """)
                .paramSource(DaysOfCarType.of(carTypeId, occupiedDays))
                .update();
    }

    @Transactional
    public void openDayForEveryCarType(LocalDate calendarDay) {
        lockCarTypesInOrder();
        jdbcClient.sql("""
                        insert into car_availability (car_type_id, calendar_day, reserved_cars)
                        select car_type.id, cast(:calendarDay as date), 0
                        from car_type
                        where not exists (
                            select 1 from car_availability
                            where car_availability.car_type_id = car_type.id
                              and car_availability.calendar_day = cast(:calendarDay as date))
                        """)
                .param("calendarDay", calendarDay)
                .update();
    }

    public int lockOpenDaysAndFindMostCarsReserved(long carTypeId, LocalDate firstDay) {
        return jdbcClient.sql("""
                        select reserved_cars from car_availability
                        where car_type_id = :carTypeId
                          and calendar_day >= :firstDay
                        order by calendar_day
                        for update
                        """)
                .param("carTypeId", carTypeId)
                .param("firstDay", firstDay)
                .query(Integer.class)
                .list().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
    }

    private void lockCarTypesInOrder() {
        jdbcClient.sql("select id from car_type order by id for update")
                .query(Long.class)
                .list();
    }

    private void lockDaysInOrder(long carTypeId, OccupiedDays occupiedDays) {
        jdbcClient.sql("""
                        select calendar_day from car_availability
                        where car_type_id = :carTypeId
                          and calendar_day between :firstDay and :lastDay
                        order by calendar_day
                        for update
                        """)
                .paramSource(DaysOfCarType.of(carTypeId, occupiedDays))
                .query(LocalDate.class)
                .list();
    }

    private record DaysOfCarType(long carTypeId, LocalDate firstDay, LocalDate lastDay) {

        static DaysOfCarType of(long carTypeId, OccupiedDays occupiedDays) {
            return new DaysOfCarType(carTypeId, occupiedDays.firstDay(), occupiedDays.lastDay());
        }
    }
}
