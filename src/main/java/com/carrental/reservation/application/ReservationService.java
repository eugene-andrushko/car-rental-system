package com.carrental.reservation.application;

import com.carrental.fleet.domain.CarType;
import com.carrental.fleet.domain.UnknownCarTypeException;
import com.carrental.fleet.persistence.CarTypeRepository;
import com.carrental.reservation.domain.IdempotencyKeyReusedException;
import com.carrental.reservation.domain.NoCarAvailableException;
import com.carrental.reservation.domain.OccupiedDays;
import com.carrental.reservation.domain.Reservation;
import com.carrental.reservation.domain.ReservationNotFoundException;
import com.carrental.reservation.domain.ReservationPeriod;
import com.carrental.reservation.domain.ReservationRequest;
import com.carrental.reservation.domain.ReservationRules;
import com.carrental.reservation.persistence.CarAvailabilityRepository;
import com.carrental.reservation.persistence.ReservationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

@Service
public class ReservationService {

    private final CarTypeRepository carTypeRepository;
    private final ReservationRepository reservationRepository;
    private final CarAvailabilityRepository carAvailabilityRepository;
    private final ReservationRules reservationRules;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    ReservationService(CarTypeRepository carTypeRepository,
                       ReservationRepository reservationRepository,
                       CarAvailabilityRepository carAvailabilityRepository,
                       ReservationRules reservationRules,
                       TransactionTemplate transactionTemplate,
                       Clock clock) {
        this.carTypeRepository = carTypeRepository;
        this.reservationRepository = reservationRepository;
        this.carAvailabilityRepository = carAvailabilityRepository;
        this.reservationRules = reservationRules;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    public Reservation reserve(ReservationRequest request) {
        try {
            return transactionTemplate.execute(_ -> findPreviousOrRejectReusedKey(request)
                    .orElseGet(() -> createReservation(request)));
        } catch (DataIntegrityViolationException keyTakenByConcurrentRequest) {
            return transactionTemplate.execute(_ -> findPreviousOrRejectReusedKey(request)
                    .orElseThrow(() -> keyTakenByConcurrentRequest));
        }
    }

    public void cancel(UUID reservationId, String customerId) {
        transactionTemplate.executeWithoutResult(_ -> {
            if (reservationRepository.cancelIfActiveAndOwnedBy(reservationId, customerId) == 1) {
                releaseCarOnEachDay(reservationRepository.findById(reservationId).orElseThrow());
            } else if (!reservationRepository.existsByIdAndCustomerId(reservationId, customerId)) {
                throw new ReservationNotFoundException(reservationId);
            }
        });
    }

    private Optional<Reservation> findPreviousOrRejectReusedKey(ReservationRequest request) {
        return reservationRepository.findByCustomerIdAndIdempotencyKey(request.customerId(), request.idempotencyKey())
                .map(previousReservation -> {
                    if (!previousReservation.isForSameCarTypeAndPeriod(request)) {
                        throw new IdempotencyKeyReusedException(request.idempotencyKey());
                    }
                    return previousReservation;
                });
    }

    private Reservation createReservation(ReservationRequest request) {
        CarType carType = carTypeRepository.findByName(request.carTypeName())
                .orElseThrow(() -> new UnknownCarTypeException(request.carTypeName()));
        ReservationPeriod period = reservationRules.validateAndCreatePeriod(request.startsAt(), request.days(), clock.instant());
        Reservation reservation = reservationRepository.saveAndFlush(
                Reservation.create(carType, request.customerId(), request.idempotencyKey(), period));
        reserveCarOnEachDay(carType, period);
        return reservation;
    }

    private void reserveCarOnEachDay(CarType carType, ReservationPeriod period) {
        OccupiedDays occupiedDays = period.calculateOccupiedDays();
        if (carAvailabilityRepository.reserveCarOnEachDay(carType.id(), occupiedDays) < occupiedDays.countDays()) {
            throw new NoCarAvailableException(carType.name(), period);
        }
    }

    private void releaseCarOnEachDay(Reservation reservation) {
        carAvailabilityRepository.releaseCarOnEachDay(
                reservation.carType().id(), reservation.period().calculateOccupiedDays());
    }
}
