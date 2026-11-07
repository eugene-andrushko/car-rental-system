package com.carrental.reservation.web;

import com.carrental.CarRentalIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;

import static com.carrental.FixedClockConfiguration.NOW;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@CarRentalIntegrationTest
@AutoConfigureMockMvc
class ReservationApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void givenNewReservation_whenRetriedAndCancelled_thenRetryReturnsItAndCancelSucceeds() throws Exception {
        // given
        String body = """
                {"carType": "VAN", "startsAt": "%s", "days": 2, "customerId": "customer-api"}
                """.formatted(NOW.plus(Duration.ofDays(1)));
        String reservationId = JsonPath.read(postReservation(body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        // when
        postReservation(body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(reservationId));

        // then
        mockMvc.perform(delete("/reservations/{id}", reservationId).param("customerId", "customer-api"))
                .andExpect(status().isNoContent());
    }

    private ResultActions postReservation(String body) throws Exception {
        return mockMvc.perform(post("/reservations")
                .header("Idempotency-Key", "key-api")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
