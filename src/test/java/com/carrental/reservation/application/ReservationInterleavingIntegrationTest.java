package com.carrental.reservation.application;

import com.carrental.CarRentalIntegrationTest;
import com.carrental.reservation.domain.KeyClaimedByConcurrentRequestException;
import com.carrental.reservation.domain.NoCarAvailableException;
import com.carrental.reservation.domain.Reservation;
import com.carrental.reservation.domain.ReservationStatus;
import com.carrental.reservation.persistence.CarAvailabilityRepository;
import com.carrental.reservation.persistence.ReservationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.carrental.reservation.application.ReservationRequestFixtures.DEFAULT_DAYS;
import static com.carrental.reservation.application.ReservationRequestFixtures.TOMORROW;
import static com.carrental.reservation.application.ReservationRequestFixtures.van;
import static com.carrental.reservation.application.ReservationRequestFixtures.withCarType;
import static com.carrental.reservation.application.ReservationRequestFixtures.withPeriod;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

@CarRentalIntegrationTest
class ReservationInterleavingIntegrationTest {

    private static final Duration PROVEN_BLOCKED_AFTER = Duration.ofMillis(500);

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationRepository reservationRepository;

    @MockitoSpyBean
    private CarAvailabilityRepository carAvailabilityRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final CountDownLatch daysReserved = new CountDownLatch(1);
    private final CountDownLatch commitAllowed = new CountDownLatch(1);

    @AfterEach
    void finishPausedReservation() {
        commitAllowed.countDown();
        executor.close();
    }

    @Test
    void givenVanDaysReservedButNotCommitted_whenAnotherCustomerReservesSameDays_thenWaitsAndGetsNoCarAvailable() throws Exception {
        // given
        Future<Reservation> firstReservation = startPausedReservation(() -> reservationService.reserve(van()));

        // when
        Future<Reservation> secondReservation = executor.submit(() -> reservationService.reserve(van()));

        // then
        assertKeepsWaiting(secondReservation);
        commitAllowed.countDown();
        assertThat(firstReservation.get().status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThatThrownBy(secondReservation::get).hasCauseInstanceOf(NoCarAvailableException.class);
        assertThat(reservationRepository.count()).isEqualTo(1);
    }

    @Test
    void givenVanDaysReservedButNotCommitted_whenAnotherCustomerReservesVanOnOtherDays_thenReservedWithoutWaiting() throws Exception {
        // given
        Future<Reservation> firstReservation = startPausedReservation(() -> reservationService.reserve(van()));
        ReservationRequest laterRequest = withPeriod(van(), TOMORROW.plus(Duration.ofDays(10)), DEFAULT_DAYS);

        // when
        Reservation laterReservation = reservationService.reserve(laterRequest);

        // then
        assertThat(laterReservation.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(firstReservation.isDone()).isFalse();
        commitAllowed.countDown();
        assertThat(firstReservation.get().status()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void givenVanDaysReservedButNotCommitted_whenSameRequestSentAgain_thenWaitsAndGetsConflictAndNextRetryGetsSameReservation() throws Exception {
        // given
        ReservationRequest request = van();
        Future<Reservation> firstReservation = startPausedReservation(() -> reservationService.reserve(request));

        // when
        Future<Reservation> concurrentReservation = executor.submit(() -> reservationService.reserve(request));

        // then
        assertKeepsWaiting(concurrentReservation);
        commitAllowed.countDown();
        assertThatThrownBy(concurrentReservation::get).hasCauseInstanceOf(KeyClaimedByConcurrentRequestException.class);
        assertThat(reservationService.reserve(request).id()).isEqualTo(firstReservation.get().id());
        assertThat(reservationRepository.count()).isEqualTo(1);
    }

    @Test
    void givenVanDaysReservedButNotCommitted_whenSameKeyUsedForSedan_thenSedanGetsConflict() throws Exception {
        // given
        ReservationRequest vanRequest = van();
        Future<Reservation> vanReservation = startPausedReservation(() -> reservationService.reserve(vanRequest));

        // when
        Future<Reservation> sedanReservation = executor.submit(() -> reservationService.reserve(withCarType(vanRequest, "SEDAN")));

        // then
        assertKeepsWaiting(sedanReservation);
        commitAllowed.countDown();
        assertThat(vanReservation.get().carType().name()).isEqualTo("VAN");
        assertThatThrownBy(sedanReservation::get).hasCauseInstanceOf(KeyClaimedByConcurrentRequestException.class);
        assertThat(reservationRepository.count()).isEqualTo(1);
    }

    @Test
    void givenCallersInRepeatableReadTransactions_whenBothReserveTheOnlyVan_thenOnlyOneReservationIsStored() throws Exception {
        // given
        TransactionTemplate repeatableRead = new TransactionTemplate(transactionManager);
        repeatableRead.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        Future<Reservation> firstReservation = startPausedReservation(() ->
                repeatableRead.execute(_ -> reservationService.reserve(van())));

        // when
        Future<Reservation> secondReservation = executor.submit(() ->
                repeatableRead.execute(_ -> reservationService.reserve(van())));

        // then
        assertKeepsWaiting(secondReservation);
        commitAllowed.countDown();
        assertThat(firstReservation.get().status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThatThrownBy(secondReservation::get).isInstanceOf(ExecutionException.class);
        assertThat(reservationRepository.count()).isEqualTo(1);
    }

    private Future<Reservation> startPausedReservation(Callable<Reservation> reservation) throws InterruptedException {
        pauseAfterNextDaysReserved();
        Future<Reservation> pausedReservation = executor.submit(reservation);
        assertThat(daysReserved.await(5, TimeUnit.SECONDS)).isTrue();
        return pausedReservation;
    }

    private void pauseAfterNextDaysReserved() {
        Answer<?> realCall = mockingDetails(carAvailabilityRepository).getMockCreationSettings().getDefaultAnswer();
        doAnswer(invocation -> {
            Object reservedDayCount = realCall.answer(invocation);
            daysReserved.countDown();
            commitAllowed.await(10, TimeUnit.SECONDS);
            return reservedDayCount;
        }).doAnswer(realCall).when(carAvailabilityRepository).reserveCarOnEachAvailableDay(anyLong(), any());
    }

    private static void assertKeepsWaiting(Future<?> reservation) {
        await().during(PROVEN_BLOCKED_AFTER).atMost(PROVEN_BLOCKED_AFTER.multipliedBy(2)).until(() -> !reservation.isDone());
    }
}
