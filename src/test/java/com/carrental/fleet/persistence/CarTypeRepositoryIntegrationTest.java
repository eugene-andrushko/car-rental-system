package com.carrental.fleet.persistence;

import com.carrental.CarRentalIntegrationTest;
import com.carrental.fleet.domain.CarType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Map;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@CarRentalIntegrationTest
class CarTypeRepositoryIntegrationTest {

    @Autowired
    private CarTypeRepository carTypeRepository;

    @Test
    void givenFreshDatabase_whenApplicationStarts_thenThreeCarTypesWithLimitedCarsAreSeeded() {
        // given, when
        Map<String, Integer> carCountByTypeName = carTypeRepository.findAll().stream()
                .collect(toMap(CarType::name, CarType::carCount));

        // then
        assertThat(carCountByTypeName).containsExactlyInAnyOrderEntriesOf(Map.of("SEDAN", 3, "SUV", 2, "VAN", 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Truck", " TRUCK"})
    void givenNameThatRequestsCanNeverMatch_whenSaveCarType_thenRejected(String name) {
        // given
        CarType carType = new CarType(name, 2);

        // when, then
        assertThatThrownBy(() -> carTypeRepository.saveAndFlush(carType))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(carTypeRepository.count()).isEqualTo(3);
    }
}
