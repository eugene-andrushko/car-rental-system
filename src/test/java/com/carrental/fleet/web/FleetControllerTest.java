package com.carrental.fleet.web;

import com.carrental.fleet.application.FleetService;
import com.carrental.fleet.domain.CarType;
import com.carrental.fleet.domain.CarsAlreadyReservedException;
import com.carrental.fleet.domain.UnknownCarTypeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FleetController.class)
class FleetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FleetService fleetService;

    @Test
    void givenValidCarCount_whenChangeCarCount_thenOkWithNewCount() throws Exception {
        // given
        when(fleetService.changeCarCount("sedan", 4)).thenReturn(new CarType("SEDAN", 4));

        // when
        ResultActions response = putCarCount("sedan", """
                {"carCount": 4}
                """);

        // then
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("SEDAN"))
                .andExpect(jsonPath("$.carCount").value(4));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"carCount\": -1}", "{}"})
    void givenMissingOrNegativeCarCount_whenChangeCarCount_thenBadRequest(String body) throws Exception {
        // given, when
        ResultActions response = putCarCount("SEDAN", body);

        // then
        response.andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @MethodSource("carCountChangeRejections")
    void givenServiceRejectsChange_whenChangeCarCount_thenMatchingStatus(RuntimeException rejection, int expectedStatus) throws Exception {
        // given
        when(fleetService.changeCarCount(anyString(), anyInt())).thenThrow(rejection);

        // when
        ResultActions response = putCarCount("SEDAN", """
                {"carCount": 1}
                """);

        // then
        response.andExpect(status().is(expectedStatus));
    }

    private ResultActions putCarCount(String carTypeName, String body) throws Exception {
        return mockMvc.perform(put("/car-types/{carTypeName}/car-count", carTypeName)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static Stream<Arguments> carCountChangeRejections() {
        return Stream.of(
                Arguments.of(new UnknownCarTypeException("TRUCK"), 404),
                Arguments.of(new CarsAlreadyReservedException("SEDAN", 1), 409));
    }
}
