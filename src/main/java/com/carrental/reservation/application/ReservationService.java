package com.carrental.reservation.application;

import com.carrental.fleet.domain.CarType;
import com.carrental.fleet.domain.UnknownCarTypeException;
import com.carrental.fleet.persistence.CarTypeRepository;
import com.carrental.reservation.domain.IdempotencyKeyReusedException;
import com.carrental.reservation.domain.KeyClaimedByConcurrentRequestException;
import com.carrental.reservation.domain.NoCarAvailableException;
import com.carrental.reservation.domain.OccupiedDays;
import com.carrental.reservation.domain.Reservation;
import com.carrental.reservation.domain.PickUpTimePassedException;
import com.carrental.reservation.domain.ReservationNotFoundException;
import com.carrental.reservation.domain.ReservationPeriod;
import com.carrental.reservation.domain.ReservationRules;
import com.carrental.reservation.persistence.CarAvailabilityRepository;
import com.carrental.reservation.persistence.ReservationRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
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

    @Transactional
    public Reservation reserve(ReservationRequest request) {
        Optional<Reservation> previousReservation =
                reservationRepository.findByCustomerIdAndIdempotencyKey(request.customerId(), request.idempotencyKey());
        if (previousReservation.isEmpty()) {
            return createReservation(request);
        }
        if (!previousReservation.get().isForSameCarTypeAndPeriod(request.carTypeName(), request.pickUpAt(), request.days())) {
            throw new IdempotencyKeyReusedException(request.idempotencyKey());
        }
        return previousReservation.get();
    }

    @Transactional
    public void cancel(UUID reservationId, String customerId) {
        Reservation reservation = reservationRepository.findByIdAndCustomerId(reservationId, customerId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        if (reservation.isCancelled()) {
            return;
        }
        if (reservation.period().isPickUpTimeReachedBy(clock.instant())) {
            throw new PickUpTimePassedException(reservationId);
        }
        if (reservationRepository.cancelIfConfirmedAndOwnedBy(reservationId, customerId) == 1) {
            carAvailabilityRepository.releaseCarOnEachDay(
                    reservation.carType().id(), reservation.period().calculateOccupiedDays());
            log.info("Cancelled reservation {}", reservationId);
        }
    }

    private Reservation createReservation(ReservationRequest request) {
        CarType carType = carTypeRepository.findByName(request.carTypeName())
                .orElseThrow(() -> new UnknownCarTypeException(request.carTypeName()));
        ReservationPeriod period = reservationRules.validateAndCreatePeriod(request.pickUpAt(), request.days(), clock.instant());
        Reservation reservation = saveAndClaimIdempotencyKey(
                Reservation.create(carType, request.customerId(), request.idempotencyKey(), period));
        reserveCarOnEachDayOrReject(carType, period);
        log.info("Reserved {} {} from {} to {}", reservation.id(), carType.name(), period.pickUpAt(), period.returnAt());
        return reservation;
    }

    private Reservation saveAndClaimIdempotencyKey(Reservation reservation) {
        try {
            return reservationRepository.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException integrityViolation) {
            if (!isUniqueKeyViolation(integrityViolation)) {
                throw integrityViolation;
            }
            log.info("Idempotency key {} was claimed by a concurrent request", reservation.idempotencyKey());
            throw new KeyClaimedByConcurrentRequestException(reservation.idempotencyKey(), integrityViolation);
        }
    }

    private static boolean isUniqueKeyViolation(DataIntegrityViolationException integrityViolation) {
        return integrityViolation.getCause() instanceof ConstraintViolationException constraintViolation
                && constraintViolation.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE;
    }

    private void reserveCarOnEachDayOrReject(CarType carType, ReservationPeriod period) {
        OccupiedDays occupiedDays = period.calculateOccupiedDays();
        int daysReserved = carAvailabilityRepository.reserveCarOnEachAvailableDay(carType.id(), occupiedDays);
        if (daysReserved < occupiedDays.countDays()) {
            throw new NoCarAvailableException(carType.name(), period);
        }
    }
}
