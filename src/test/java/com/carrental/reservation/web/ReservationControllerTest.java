package com.carrental.reservation.web;

import com.carrental.fleet.domain.CarType;
import com.carrental.fleet.domain.UnknownCarTypeException;
import com.carrental.reservation.application.ReservationService;
import com.carrental.reservation.domain.IdempotencyKeyReusedException;
import com.carrental.reservation.domain.InvalidReservationException;
import com.carrental.reservation.domain.NoCarAvailableException;
import com.carrental.reservation.domain.Reservation;
import com.carrental.reservation.domain.ReservationAlreadyStartedException;
import com.carrental.reservation.domain.ReservationNotFoundException;
import com.carrental.reservation.domain.ReservationPeriod;
import com.carrental.reservation.application.ReservationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReservationController.class)
class ReservationControllerTest {

    private static final Instant STARTS_AT = Instant.parse("2030-01-01T10:00:00Z");
    private static final UUID RESERVATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String VALID_BODY = """
            {"carType": "suv", "startsAt": "2030-01-01T10:00:00Z", "days": 3, "customerId": "customer-1"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService reservationService;

    @Test
    void givenValidRequest_whenReserve_thenCreatedWithReservation() throws Exception {
        // given
        ReservationRequest expectedRequest = new ReservationRequest("suv", STARTS_AT, 3, "customer-1", "key-1");
        Reservation reservation = Reservation.create(new CarType("SUV", 2), "customer-1", "key-1", ReservationPeriod.ofDays(STARTS_AT, 3));
        when(reservationService.reserve(expectedRequest)).thenReturn(reservation);

        // when
        ResultActions response = postReservation("key-1", VALID_BODY);

        // then
        response.andExpect(status().isCreated())
                .andExpect(jsonPath("$.carType").value("SUV"))
                .andExpect(jsonPath("$.startsAt").value("2030-01-01T10:00:00Z"))
                .andExpect(jsonPath("$.endsAt").value("2030-01-04T10:00:00Z"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void givenDaysNotPositive_whenReserve_thenBadRequest() throws Exception {
        // given
        String body = """
                {"carType": "SUV", "startsAt": "2030-01-01T10:00:00Z", "days": 0, "customerId": "customer-1"}
                """;

        // when
        ResultActions response = postReservation("key-1", body);

        // then
        response.andExpect(status().isBadRequest());
    }

    @Test
    void givenNoIdempotencyKey_whenReserve_thenBadRequest() throws Exception {
        // given, when
        ResultActions response = mockMvc.perform(post("/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY));

        // then
        response.andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @MethodSource("reservationRejections")
    void givenServiceRejectsReservation_whenReserve_thenMatchingStatus(RuntimeException rejection, int expectedStatus) throws Exception {
        // given
        when(reservationService.reserve(any())).thenThrow(rejection);

        // when
        ResultActions response = postReservation("key-1", VALID_BODY);

        // then
        response.andExpect(status().is(expectedStatus));
    }

    @Test
    void givenLockFailure_whenReserve_thenServiceUnavailableWithRetryAfter() throws Exception {
        // given
        when(reservationService.reserve(any())).thenThrow(new CannotAcquireLockException("lock timeout"));

        // when
        ResultActions response = postReservation("key-1", VALID_BODY);

        // then
        response.andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "1"));
    }

    @Test
    void givenOwnReservation_whenCancel_thenNoContent() throws Exception {
        // given, when
        ResultActions response = mockMvc.perform(delete("/reservations/{id}", RESERVATION_ID).param("customerId", "customer-1"));

        // then
        response.andExpect(status().isNoContent());
    }

    @ParameterizedTest
    @MethodSource("cancelRejections")
    void givenServiceRejectsCancel_whenCancel_thenMatchingStatus(RuntimeException rejection, int expectedStatus) throws Exception {
        // given
        doThrow(rejection).when(reservationService).cancel(eq(RESERVATION_ID), anyString());

        // when
        ResultActions response = mockMvc.perform(delete("/reservations/{id}", RESERVATION_ID).param("customerId", "customer-1"));

        // then
        response.andExpect(status().is(expectedStatus));
    }

    private ResultActions postReservation(String idempotencyKey, String body) throws Exception {
        return mockMvc.perform(post("/reservations")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static Stream<Arguments> reservationRejections() {
        return Stream.of(
                Arguments.of(new InvalidReservationException("start in the past"), 400),
                Arguments.of(new UnknownCarTypeException("TRUCK"), 404),
                Arguments.of(new NoCarAvailableException("SUV", ReservationPeriod.ofDays(STARTS_AT, 3)), 409),
                Arguments.of(new IdempotencyKeyReusedException("key-1"), 422));
    }

    private static Stream<Arguments> cancelRejections() {
        return Stream.of(
                Arguments.of(new ReservationNotFoundException(RESERVATION_ID), 404),
                Arguments.of(new ReservationAlreadyStartedException(RESERVATION_ID), 409));
    }
}
