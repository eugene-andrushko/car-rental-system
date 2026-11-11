package com.carrental.fleet.application;

import com.carrental.fleet.domain.CarType;
import com.carrental.fleet.domain.CarsAlreadyReservedException;
import com.carrental.fleet.domain.UnknownCarTypeException;
import com.carrental.fleet.persistence.CarAvailabilityRepository;
import com.carrental.fleet.persistence.CarTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Service
public class FleetService {

    private static final Logger log = LoggerFactory.getLogger(FleetService.class);

    private final CarTypeRepository carTypeRepository;
    private final CarAvailabilityRepository carAvailabilityRepository;
    private final Clock clock;

    FleetService(CarTypeRepository carTypeRepository, CarAvailabilityRepository carAvailabilityRepository, Clock clock) {
        this.carTypeRepository = carTypeRepository;
        this.carAvailabilityRepository = carAvailabilityRepository;
        this.clock = clock;
    }

    @Transactional
    public CarType changeCarCount(String carTypeName, int newCarCount) {
        CarType carType = carTypeRepository.findAndLockTypeByName(CarType.normalizeName(carTypeName))
                .orElseThrow(() -> new UnknownCarTypeException(carTypeName));
        int mostCarsReserved = carAvailabilityRepository.lockOpenDaysAndFindMostCarsReserved(carType.id(), LocalDate.now(clock));
        if (mostCarsReserved > newCarCount) {
            throw new CarsAlreadyReservedException(carType.name(), newCarCount);
        }
        log.info("Changed {} from {} to {} cars", carType.name(), carType.carCount(), newCarCount);
        carType.changeCarCount(newCarCount);
        return carType;
    }
}
