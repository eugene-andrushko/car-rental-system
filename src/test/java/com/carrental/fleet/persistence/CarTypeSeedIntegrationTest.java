package com.carrental.fleet.persistence;

import com.carrental.fleet.domain.CarType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CarTypeSeedIntegrationTest {

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
}
