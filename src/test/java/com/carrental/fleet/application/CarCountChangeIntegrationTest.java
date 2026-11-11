package com.carrental.fleet.application;

import com.carrental.CarRentalIntegrationTest;
import com.carrental.fleet.domain.CarsAlreadyReservedException;
import com.carrental.fleet.domain.UnknownCarTypeException;
import com.carrental.fleet.persistence.CarTypeRepository;
import com.carrental.reservation.application.ReservationService;
import com.carrental.reservation.domain.NoCarAvailableException;
import com.carrental.reservation.domain.Reservation;
import com.carrental.reservation.domain.ReservationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static com.carrental.FixedClockConfiguration.NOW;
import static com.carrental.reservation.application.ReservationRequestFixtures.TOMORROW;
import static com.carrental.reservation.application.ReservationRequestFixtures.ofCarType;
import static com.carrental.reservation.application.ReservationRequestFixtures.van;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@CarRentalIntegrationTest
class CarCountChangeIntegrationTest {

    private static final LocalDate TODAY = LocalDate.ofInstant(NOW, ZoneOffset.UTC);
    private static final LocalDate PICK_UP_DAY = LocalDate.ofInstant(TOMORROW, ZoneOffset.UTC);

    @Autowired
    private FleetService fleetService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private CarTypeRepository carTypeRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void givenTheOnlyVanReserved_whenSecondVanAdded_thenAnotherVanCanBeReservedForSameDays() {
        // given
        reservationService.reserve(van());

        // when
        fleetService.changeCarCount("van", 2);

        // then
        assertThat(reservationService.reserve(van()).status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(carTypeRepository.findByName("VAN").orElseThrow().carCount()).isEqualTo(2);
    }

    @Test
    void givenNoSedanReserved_whenOneSedanRemoved_thenEveryOpenDayHasOneSedanLess() {
        // given, when
        fleetService.changeCarCount("SEDAN", 2);

        // then
        assertThat(findAvailableCarCounts("SEDAN")).containsOnly(2);
        assertThat(carTypeRepository.findByName("SEDAN").orElseThrow().carCount()).isEqualTo(2);
    }

    @Test
    void givenTheOnlyVanReserved_whenVanRemoved_thenRejectedAndNothingChanged() {
        // given
        reservationService.reserve(van());

        // when, then
        assertThatThrownBy(() -> fleetService.changeCarCount("VAN", 0))
                .isInstanceOf(CarsAlreadyReservedException.class);
        assertThat(carTypeRepository.findByName("VAN").orElseThrow().carCount()).isEqualTo(1);
        assertThat(findAvailableCars("VAN", TODAY)).isEqualTo(1);
        assertThat(findAvailableCars("VAN", PICK_UP_DAY)).isZero();
    }

    @Test
    void givenSuvsRemoved_whenReserveSuv_thenNoCarAvailable() {
        // given
        fleetService.changeCarCount("SUV", 0);

        // when, then
        assertThatThrownBy(() -> reservationService.reserve(ofCarType("SUV")))
                .isInstanceOf(NoCarAvailableException.class);
    }

    @Test
    void givenUnknownCarType_whenChangeCarCount_thenRejected() {
        // given, when, then
        assertThatThrownBy(() -> fleetService.changeCarCount("TRUCK", 2))
                .isInstanceOf(UnknownCarTypeException.class);
    }

    private List<Integer> findAvailableCarCounts(String carTypeName) {
        return jdbcClient.sql("""
                        select car_type.car_count - car_availability.reserved_cars
                        from car_availability join car_type on car_type.id = car_availability.car_type_id
                        where car_type.name = :carTypeName
                        """)
                .param("carTypeName", carTypeName)
                .query(Integer.class)
                .list();
    }

    private int findAvailableCars(String carTypeName, LocalDate calendarDay) {
        return jdbcClient.sql("""
                        select car_type.car_count - car_availability.reserved_cars
                        from car_availability join car_type on car_type.id = car_availability.car_type_id
                        where car_type.name = :carTypeName
                          and calendar_day = :calendarDay
                        """)
                .param("carTypeName", carTypeName)
                .param("calendarDay", calendarDay)
                .query(Integer.class)
                .single();
    }
}
