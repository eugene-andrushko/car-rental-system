package com.carrental.reservation.web;

import com.carrental.CarRentalIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@CarRentalIntegrationTest
@AutoConfigureMockMvc
class OpenApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void givenRunningApplication_whenFetchApiDocs_thenReservationEndpointsAreDocumented() throws Exception {
        // given, when
        ResultActions response = mockMvc.perform(get("/v3/api-docs"));

        // then
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Car Rental API"))
                .andExpect(jsonPath("$.paths['/reservations'].post").exists())
                .andExpect(jsonPath("$.paths['/reservations/{reservationId}'].delete").exists());
    }

    @Test
    void givenRunningApplication_whenOpenSwaggerUi_thenPageIsServed() throws Exception {
        // given, when
        ResultActions response = mockMvc.perform(get("/swagger-ui/index.html"));

        // then
        response.andExpect(status().isOk());
    }
}
