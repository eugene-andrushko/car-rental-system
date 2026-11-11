package com.carrental.reservation.web;

import com.carrental.CarRentalIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.carrental.FixedClockConfiguration.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@CarRentalIntegrationTest
@AutoConfigureMockMvc
class ReservationApiIntegrationTest {

    private static final Instant TOMORROW = NOW.plus(Duration.ofDays(1));
    private static final String VAN_BODY = """
            {"carType": "VAN", "pickUpAt": "%s", "days": 2, "customerId": "customer-api"}
            """.formatted(TOMORROW);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void givenNewReservation_whenRetriedAndCancelled_thenRetryReturnsItAndCancelSucceeds() throws Exception {
        // given
        String reservationId = JsonPath.read(postReservation(VAN_BODY)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        // when
        postReservation(VAN_BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(reservationId));

        // then
        mockMvc.perform(delete("/reservations/{id}", reservationId).param("customerId", "customer-api"))
                .andExpect(status().isNoContent());
    }

    @Test
    void givenVanDayLockedLongerThanLockTimeout_whenReserve_thenServiceUnavailableWithRetryAfter() throws Exception {
        // given
        CountDownLatch dayLocked = new CountDownLatch(1);
        CountDownLatch lockReleaseAllowed = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> lockHolder = executor.submit(() -> holdVanDayLock(dayLocked, lockReleaseAllowed));
            assertThat(dayLocked.await(5, TimeUnit.SECONDS)).isTrue();

            // when
            ResultActions response = postReservation(VAN_BODY);

            // then
            lockReleaseAllowed.countDown();
            lockHolder.get(10, TimeUnit.SECONDS);
            response.andExpect(status().isServiceUnavailable())
                    .andExpect(header().string("Retry-After", "1"));
        }
    }

    private void holdVanDayLock(CountDownLatch dayLocked, CountDownLatch lockReleaseAllowed) {
        new TransactionTemplate(transactionManager).executeWithoutResult(_ -> {
            jdbcClient.sql("""
                            select reserved_cars from car_availability
                            where car_type_id = (select id from car_type where name = 'VAN')
                              and calendar_day = :calendarDay
                            for update
                            """)
                    .param("calendarDay", LocalDate.ofInstant(TOMORROW, ZoneOffset.UTC))
                    .query(Integer.class)
                    .single();
            dayLocked.countDown();
            awaitLockRelease(lockReleaseAllowed);
        });
    }

    private static void awaitLockRelease(CountDownLatch latch) {
        try {
            latch.await(20, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private ResultActions postReservation(String body) throws Exception {
        return mockMvc.perform(post("/reservations")
                .header("Idempotency-Key", "key-api")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
