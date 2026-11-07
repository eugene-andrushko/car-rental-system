package com.carrental.fleet.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CarTypeTest {

    @Test
    void givenLowerCaseNameWithSpaces_whenNormalize_thenUpperCaseTrimmed() {
        // given
        String name = " suv ";

        // when
        String normalizedName = CarType.normalizeName(name);

        // then
        assertThat(normalizedName).isEqualTo("SUV");
    }
}
