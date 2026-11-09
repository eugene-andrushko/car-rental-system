package com.carrental.reservation.application;

import com.carrental.CarRentalIntegrationTest;
import com.carrental.fleet.domain.UnknownCarTypeException;
import com.carrental.fleet.persistence.CarTypeRepository;
import com.carrental.reservation.domain.IdempotencyKeyReusedException;
import com.carrental.reservation.domain.KeyClaimedByConcurrentRequestException;
import com.carrental.reservation.domain.NoCarAvailableException;
import com.carrental.reservation.domain.Reservation;
import com.carrental.reservation.domain.PickUpTimePassedException;
import com.carrental.reservation.domain.ReservationNotFoundException;
import com.carrental.reservation.domain.ReservationPeriod;
import com.carrental.reservation.domain.ReservationStatus;
import com.carrental.reservation.persistence.ReservationRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.carrental.FixedClockConfiguration.NOW;
import static com.carrental.reservation.application.RaceStarter.race;
import static com.carrental.reservation.application.ReservationRequestFixtures.DEFAULT_DAYS;
import static com.carrental.reservation.application.ReservationRequestFixtures.TOMORROW;
import static com.carrental.reservation.application.ReservationRequestFixtures.generateCustomerId;
import static com.carrental.reservation.application.ReservationRequestFixtures.ofCarType;
import static com.carrental.reservation.application.ReservationRequestFixtures.van;
import static com.carrental.reservation.application.ReservationRequestFixtures.withCustomerId;
import static com.carrental.reservation.application.ReservationRequestFixtures.withPeriod;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@CarRentalIntegrationTest
class ReservationServiceIntegrationTest {

    private static final int RACING_CALLERS = 8;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private CarTypeRepository carTypeRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @Nested
    class Reserve {

        @ParameterizedTest
        @ValueSource(strings = {"SEDAN", "SUV", "VAN"})
        void givenSeededCarType_whenReserve_thenConfirmedReservationIsStored(String carTypeName) {
            // given
            ReservationRequest request = ofCarType(carTypeName);

            // when
            Reservation reservation = reservationService.reserve(request);

            // then
            Reservation storedReservation = reservationRepository.findById(reservation.id()).orElseThrow();
            assertThat(storedReservation.carType().name()).isEqualTo(carTypeName);
            assertThat(storedReservation.period()).isEqualTo(ReservationPeriod.ofDays(TOMORROW, DEFAULT_DAYS));
            assertThat(storedReservation.status()).isEqualTo(ReservationStatus.CONFIRMED);
        }

        @Test
        void givenTheOnlyVanReserved_whenReserveOverlappingVan_thenNoCarAvailableAndNothingStored() {
            // given
            reservationService.reserve(van());
            ReservationRequest overlappingRequest = withPeriod(van(), TOMORROW.plus(Duration.ofDays(1)), 1);

            // when, then
            assertThatThrownBy(() -> reservationService.reserve(overlappingRequest))
                    .isInstanceOf(NoCarAvailableException.class);
            assertThat(reservationRepository.count()).isEqualTo(1);
        }

        @Test
        void givenSpanningReservationRejected_whenReserveAvailableDayOfThatSpan_thenReserved() {
            // given
            reservationService.reserve(withPeriod(van(), TOMORROW.plus(Duration.ofDays(2)), 1));
            ReservationRequest spanningRequest = withPeriod(van(), TOMORROW, 5);
            assertThatThrownBy(() -> reservationService.reserve(spanningRequest))
                    .isInstanceOf(NoCarAvailableException.class);

            // when
            Reservation reservationBeforeBookedDays = reservationService.reserve(withPeriod(van(), TOMORROW, 1));

            // then
            assertThat(reservationBeforeBookedDays.status()).isEqualTo(ReservationStatus.CONFIRMED);
        }

        @Test
        void givenTheOnlyVanReserved_whenNextPickUpIsOnReturnDay_thenNoCarAvailable() {
            // given
            Reservation reservation = reservationService.reserve(van());
            ReservationRequest returnDayRequest = withPeriod(van(), reservation.period().returnAt(), 1);

            // when, then
            assertThatThrownBy(() -> reservationService.reserve(returnDayRequest))
                    .isInstanceOf(NoCarAvailableException.class);
        }

        @Test
        void givenTheOnlyVanReserved_whenNextPickUpIsDayAfterReturn_thenVanIsReserved() {
            // given
            Reservation reservation = reservationService.reserve(van());
            ReservationRequest nextDayRequest = withPeriod(van(), reservation.period().returnAt().plus(Duration.ofDays(1)), 1);

            // when
            Reservation nextReservation = reservationService.reserve(nextDayRequest);

            // then
            assertThat(nextReservation.status()).isEqualTo(ReservationStatus.CONFIRMED);
        }

        @Test
        void givenTheOnlyVanReserved_whenReserveSuvForSamePeriod_thenSuvIsReserved() {
            // given
            reservationService.reserve(van());

            // when
            Reservation reservation = reservationService.reserve(ofCarType("SUV"));

            // then
            assertThat(reservation.carType().name()).isEqualTo("SUV");
        }

        @Test
        void givenUnknownCarType_whenReserve_thenRejected() {
            // given
            ReservationRequest request = ofCarType("TRUCK");

            // when, then
            assertThatThrownBy(() -> reservationService.reserve(request))
                    .isInstanceOf(UnknownCarTypeException.class);
        }

        @Test
        void givenCustomerIdLongerThanColumn_whenReserve_thenIntegrityViolationIsNotTreatedAsClaimedKey() {
            // given
            ReservationRequest request = withCustomerId(van(), "c".repeat(101));

            // when, then
            assertThatThrownBy(() -> reservationService.reserve(request))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .isNotInstanceOf(KeyClaimedByConcurrentRequestException.class);
            assertThat(reservationRepository.count()).isZero();
        }

        @Test
        void givenOneVan_whenCustomersRaceToReserveIt_thenExactlyOneSucceeds() {
            // given, when
            List<Optional<Reservation>> reservations = race(RACING_CALLERS, () -> reserveIfAvailable(van()));

            // then
            assertThat(reservations).filteredOn(Optional::isPresent).hasSize(1);
            assertThat(reservationRepository.count()).isEqualTo(1);
        }

        private Optional<Reservation> reserveIfAvailable(ReservationRequest request) {
            try {
                return Optional.of(reservationService.reserve(request));
            } catch (NoCarAvailableException exception) {
                return Optional.empty();
            }
        }
    }

    @Nested
    class Retry {

        @Test
        void givenReservation_whenRetriedWithSameRequest_thenSameReservationReturned() {
            // given
            ReservationRequest request = van();
            Reservation reservation = reservationService.reserve(request);

            // when
            Reservation retriedReservation = reservationService.reserve(request);

            // then
            assertThat(retriedReservation.id()).isEqualTo(reservation.id());
            assertThat(reservationRepository.count()).isEqualTo(1);
        }

        @Test
        void givenReservation_whenSameKeyUsedForDifferentRequest_thenRejected() {
            // given
            ReservationRequest request = van();
            reservationService.reserve(request);
            ReservationRequest differentRequest = withPeriod(request, request.pickUpAt(), DEFAULT_DAYS + 1);

            // when, then
            assertThatThrownBy(() -> reservationService.reserve(differentRequest))
                    .isInstanceOf(IdempotencyKeyReusedException.class);
        }

        @Test
        void givenReservation_whenSameKeyUsedWithLatestPossiblePickUp_thenRejectedAsReusedKey() {
            // given
            ReservationRequest request = van();
            reservationService.reserve(request);
            ReservationRequest extremeRequest = withPeriod(request, Instant.MAX, Integer.MAX_VALUE);

            // when, then
            assertThatThrownBy(() -> reservationService.reserve(extremeRequest))
                    .isInstanceOf(IdempotencyKeyReusedException.class);
        }

        @Test
        void givenSameRequest_whenRetriesRace_thenOneReservationStoredAndOthersGetItOrConflict() {
            // given
            ReservationRequest request = van();

            // when
            List<Optional<Reservation>> reservations = race(RACING_CALLERS, () -> reserveUnlessKeyClaimedConcurrently(request));

            // then
            List<Reservation> returnedReservations = reservations.stream().flatMap(Optional::stream).toList();
            assertThat(returnedReservations).isNotEmpty();
            assertThat(returnedReservations).extracting(Reservation::id).containsOnly(returnedReservations.getFirst().id());
            assertThat(reservationRepository.count()).isEqualTo(1);
        }

        private Optional<Reservation> reserveUnlessKeyClaimedConcurrently(ReservationRequest request) {
            try {
                return Optional.of(reservationService.reserve(request));
            } catch (KeyClaimedByConcurrentRequestException exception) {
                return Optional.empty();
            }
        }

        @Test
        void givenCancelledReservation_whenRetried_thenCancelledReservationReturned() {
            // given
            ReservationRequest request = van();
            Reservation reservation = reservationService.reserve(request);
            reservationService.cancel(reservation.id(), request.customerId());

            // when
            Reservation retriedReservation = reservationService.reserve(request);

            // then
            assertThat(retriedReservation.id()).isEqualTo(reservation.id());
            assertThat(retriedReservation.status()).isEqualTo(ReservationStatus.CANCELLED);
        }

        @Test
        void givenSameKey_whenAnotherCustomerReserves_thenEachGetsOwnReservation() {
            // given
            ReservationRequest request = ofCarType("SUV");
            Reservation firstReservation = reservationService.reserve(request);

            // when
            Reservation secondReservation = reservationService.reserve(withCustomerId(request, generateCustomerId()));

            // then
            assertThat(secondReservation.id()).isNotEqualTo(firstReservation.id());
        }
    }

    @Nested
    class Cancel {

        @Test
        void givenVanReservation_whenOwnerCancels_thenVanCanBeReservedAgain() {
            // given
            ReservationRequest request = van();
            Reservation reservation = reservationService.reserve(request);

            // when
            reservationService.cancel(reservation.id(), request.customerId());

            // then
            assertThat(reservationService.reserve(van()).status()).isEqualTo(ReservationStatus.CONFIRMED);
        }

        @Test
        void givenReservation_whenAnotherCustomerCancels_thenRejectedAndReservationStaysConfirmed() {
            // given
            Reservation reservation = reservationService.reserve(van());

            // when, then
            assertThatThrownBy(() -> reservationService.cancel(reservation.id(), generateCustomerId()))
                    .isInstanceOf(ReservationNotFoundException.class);
            assertThat(findStatus(reservation)).isEqualTo(ReservationStatus.CONFIRMED);
        }

        @Test
        void givenPickUpTimePassed_whenOwnerCancels_thenRejectedAndReservationStaysConfirmed() {
            // given
            Instant yesterday = NOW.minus(Duration.ofDays(1));
            Reservation reservationPastPickUp = reservationRepository.saveAndFlush(Reservation.create(
                    carTypeRepository.findByName("VAN").orElseThrow(), generateCustomerId(), "key-past-pick-up",
                    ReservationPeriod.ofDays(yesterday, 3)));

            // when, then
            assertThatThrownBy(() -> reservationService.cancel(reservationPastPickUp.id(), reservationPastPickUp.customerId()))
                    .isInstanceOf(PickUpTimePassedException.class);
            assertThat(findStatus(reservationPastPickUp)).isEqualTo(ReservationStatus.CONFIRMED);
        }

        @Test
        void givenCancelledReservation_whenOwnerCancelsAgain_thenSucceeds() {
            // given
            ReservationRequest request = van();
            Reservation reservation = reservationService.reserve(request);
            reservationService.cancel(reservation.id(), request.customerId());

            // when
            reservationService.cancel(reservation.id(), request.customerId());

            // then
            assertThat(findStatus(reservation)).isEqualTo(ReservationStatus.CANCELLED);
        }

        @Test
        void givenVanReservation_whenOwnerCancelsConcurrently_thenVanIsReleasedOnce() {
            // given
            ReservationRequest request = van();
            Reservation reservation = reservationService.reserve(request);

            // when
            race(RACING_CALLERS, () -> {
                reservationService.cancel(reservation.id(), request.customerId());
                return reservation.id();
            });

            // then
            assertThat(findStatus(reservation)).isEqualTo(ReservationStatus.CANCELLED);
            assertThat(findAvailableVans(reservation.period().calculateOccupiedDays().firstDay())).isEqualTo(1);
        }

        private ReservationStatus findStatus(Reservation reservation) {
            return reservationRepository.findById(reservation.id()).orElseThrow().status();
        }

        private int findAvailableVans(LocalDate calendarDay) {
            return jdbcClient.sql("""
                            select available_cars from car_availability
                            join car_type on car_type.id = car_availability.car_type_id
                            where car_type.name = 'VAN' and calendar_day = :calendarDay
                            """)
                    .param("calendarDay", calendarDay)
                    .query(Integer.class)
                    .single();
        }
    }
}
