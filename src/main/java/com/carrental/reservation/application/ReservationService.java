package com.carrental.reservation.application;

import com.carrental.fleet.domain.CarType;
import com.carrental.fleet.domain.UnknownCarTypeException;
import com.carrental.fleet.persistence.CarTypeRepository;
import com.carrental.reservation.domain.IdempotencyKeyReusedException;
import com.carrental.reservation.domain.NoCarAvailableException;
import com.carrental.reservation.domain.OccupiedDays;
import com.carrental.reservation.domain.Reservation;
import com.carrental.reservation.domain.ReservationAlreadyStartedException;
import com.carrental.reservation.domain.ReservationNotFoundException;
import com.carrental.reservation.domain.ReservationPeriod;
import com.carrental.reservation.domain.ReservationRules;
import com.carrental.reservation.persistence.CarAvailabilityRepository;
import com.carrental.reservation.persistence.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final CarTypeRepository carTypeRepository;
    private final ReservationRepository reservationRepository;
    private final CarAvailabilityRepository carAvailabilityRepository;
    private final ReservationRules reservationRules;
    private final Clock clock;

    ReservationService(CarTypeRepository carTypeRepository,
                       ReservationRepository reservationRepository,
                       CarAvailabilityRepository carAvailabilityRepository,
                       ReservationRules reservationRules,
                       Clock clock) {
        this.carTypeRepository = carTypeRepository;
        this.reservationRepository = reservationRepository;
        this.carAvailabilityRepository = carAvailabilityRepository;
        this.reservationRules = reservationRules;
        this.clock = clock;
    }

    @Retryable(includes = KeyTakenByConcurrentRequestException.class, maxRetries = 1, delay = 0)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Reservation reserve(ReservationRequest request) {
        return findPreviousOrRejectReusedKey(request).orElseGet(() -> createReservation(request));
    }

    @Transactional
    public void cancel(UUID reservationId, String customerId) {
        Reservation reservation = reservationRepository.findByIdAndCustomerId(reservationId, customerId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        if (reservation.isCancelled()) {
            return;
        }
        if (reservation.period().isStartedAt(clock.instant())) {
            throw new ReservationAlreadyStartedException(reservationId);
        }
        if (reservationRepository.cancelIfActiveAndOwnedBy(reservationId, customerId) == 1) {
            carAvailabilityRepository.releaseCarOnEachDay(
                    reservation.carType().id(), reservation.period().calculateOccupiedDays());
            log.info("Cancelled reservation {}", reservationId);
        }
    }

    private Optional<Reservation> findPreviousOrRejectReusedKey(ReservationRequest request) {
        return reservationRepository.findByCustomerIdAndIdempotencyKey(request.customerId(), request.idempotencyKey())
                .map(previousReservation -> {
                    if (!previousReservation.isForSameCarTypeAndPeriod(request.carTypeName(), request.toReservationPeriod())) {
                        throw new IdempotencyKeyReusedException(request.idempotencyKey());
                    }
                    return previousReservation;
                });
    }

    private Reservation createReservation(ReservationRequest request) {
        CarType carType = carTypeRepository.findByName(request.carTypeName())
                .orElseThrow(() -> new UnknownCarTypeException(request.carTypeName()));
        ReservationPeriod period = reservationRules.validateAndCreatePeriod(request.startsAt(), request.days(), clock.instant());
        Reservation reservation = saveOrRejectTakenKey(
                Reservation.create(carType, request.customerId(), request.idempotencyKey(), period));
        reserveCarOnEachDayOrReject(carType, period);
        log.info("Reserved {} {} from {} to {}", reservation.id(), carType.name(), period.startsAt(), period.endsAt());
        return reservation;
    }

    private Reservation saveOrRejectTakenKey(Reservation reservation) {
        try {
            return reservationRepository.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException duplicateIdempotencyKey) {
            log.info("Idempotency key {} was taken by a concurrent request, retrying", reservation.idempotencyKey());
            throw new KeyTakenByConcurrentRequestException(duplicateIdempotencyKey);
        }
    }

    private void reserveCarOnEachDayOrReject(CarType carType, ReservationPeriod period) {
        OccupiedDays occupiedDays = period.calculateOccupiedDays();
        if (carAvailabilityRepository.reserveCarOnEachDay(carType.id(), occupiedDays) < occupiedDays.countDays()) {
            throw new NoCarAvailableException(carType.name(), period);
        }
    }
}
