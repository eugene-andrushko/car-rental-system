# Car Rental System

A simulated car rental system. You reserve a car of a given type (Sedan, SUV, Van) from a pick-up date and time for a number of days; the car is returned that many days later. Each type has a limited number of cars.

## Requirements

- Java 25

## Run

```bash
./mvnw spring-boot:run
```

The app starts on port 8080 with an in-memory H2 database. To run against PostgreSQL, set `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`, then:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres
```

## Test

```bash
./mvnw test
```

- **Unit tests** cover the domain rules: period validation, occupied days, and matching a retry to its reservation.
- **The car limit is enforced by the database** (see Trade-offs), so `@SpringBootTest` integration tests on H2 prove it. A fake of that SQL statement would prove only the fake.
- **The integration tests also force concurrent interleavings:** a request is paused while it holds its days, and the test checks who has to wait and what the second request gets.

## Seed data

At startup, Flyway seeds 3 car types: 3 sedans, 2 SUVs and 1 van (`car_type.car_count`). A calendar job then opens every bookable day for every type, with all of that type's cars available. It runs before the server accepts requests, and again on `reservation.calendar-cron` (daily at midnight UTC). Each run also opens 7 spare days past the latest return day (a reservation of the maximum length starting at the end of the booking horizon), so one missed run, or runs up to a week apart, leave no bookable day closed. A car type name must be stored upper-case and trimmed (a `check` constraint), because requests are matched that way.

## Usage

Interactive API docs (Swagger UI): <http://localhost:8080/swagger-ui.html>. The OpenAPI spec is at `/v3/api-docs`.

Reserve a car. The `Idempotency-Key` header is required. A retry with the same key and the same body returns the same `201` response. A request sent while another one with the same key is still being processed gets `409`; retrying it then returns the reservation.

```bash
curl -i -X POST localhost:8080/reservations \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: 7f3c2a' \
  -d '{"carType":"SUV","pickUpAt":"2026-12-01T10:00:00Z","days":3,"customerId":"customer-1"}'
```

```json
{"id":"…","carType":"SUV","pickUpAt":"2026-12-01T10:00:00Z","returnAt":"2026-12-04T10:00:00Z","status":"CONFIRMED"}
```

Cancel a reservation before its pick-up time. Cancelling again returns `204` too.

```bash
curl -i -X DELETE 'localhost:8080/reservations/{id}?customerId=customer-1'
```

Errors are returned as `application/problem+json`:

| Status | When |
|---|---|
| 400 | invalid body, missing `Idempotency-Key`, a `customerId` or `Idempotency-Key` longer than 100 characters, pick-up in the past or beyond the booking horizon, days outside 1–30 |
| 404 | unknown car type; reservation not found, or it belongs to another customer |
| 409 | no car of that type is available for the whole period; another request with the same `Idempotency-Key` was processed at the same time; the pick-up time has passed (cancel) |
| 422 | the `Idempotency-Key` was already used for a different request |
| 503 | a temporary database failure: a lock wait over 5 seconds, a deadlock, or a query timeout; retry with the same `Idempotency-Key` after `Retry-After` seconds |
| 500 | anything unexpected; logged with its stack trace, and the response carries only a generic message |

## Assumptions

- **You reserve a type, not a specific car**, the way rental companies sell a category. The specific car is assigned at pick-up, which is out of scope.
- **Availability is counted per calendar day (UTC).** A reservation occupies every day it touches. One picked up Jan 1 at 10:00 for 3 days is returned Jan 4 at 10:00 and occupies Jan 1–4, so a car returned on a day can go out again only the next day. It is conservative: a car can never be double-booked, but there's no same-day turnaround.
- **The period is `[pickUpAt, pickUpAt + days × 24h)`**; `returnAt` is its exclusive end, stored to the microsecond.
- **A pick-up may be anywhere from now to 90 days ahead.** A reservation lasts 1 to 30 days. Both limits are set in `application.yml` (`reservation.*`) and bound by `ReservationProperties`, so the domain rules stay free of Spring.
- **Car types are data, not code.** Adding a type means writing a migration; no Java changes.
- **Every reservation carries an idempotency key, unique per customer.** A retry with the same key and the same request returns the original reservation, even a cancelled one. Reusing the key for a different request is rejected.
- **Only the owner can cancel, and only before the pick-up time.** After it, the car is treated as in use.

## Trade-offs

- **An inventory calendar with atomic counters** (`car_availability`: available cars per type per day). A reservation reserves one car on each day it occupies in a single conditional `UPDATE … set available_cars = available_cars - 1 where … and available_cars > 0`.
  - If fewer days were updated than the period covers, the transaction rolls back.
  - The check and the decrement are one statement, so no other request can slip in between them.
  - `check (available_cars >= 0)` keeps the count from ever going negative.
  - Only reservations that share a day of the same type wait for each other, and only for that row lock. Different days and different types run in parallel, across threads and app instances.
- **The days are locked in date order** (`select … order by calendar_day for update`) before they are updated. Overlapping reservations therefore can't deadlock each other, however the database scans the table.
- **The reservation row is inserted before the counters are decremented.** Two concurrent requests with the same key then meet at the unique key. The second one waits for the first to finish, fails on the key, and gets `409`, as the IETF Idempotency-Key draft and Stripe's API do. The server doesn't retry it; the client's retry returns the reservation.
- **Cancel changes the status and gives the cars back in one transaction.** Only the request that actually moved the status from `CONFIRMED` to `CANCELLED` releases the cars, so concurrent cancels release them once. The row and its idempotency key stay, so a late retry can't recreate the reservation.
- **The domain classes are the JPA entities.** That means less code and no mappers, but the domain carries JPA annotations. The counters use plain SQL (`JdbcClient`), because the logic is one statement each.

## Limitations

- **No security.** There is no authentication or authorization. `customerId` is trusted as the caller sends it, so the ownership check on cancel is a business rule, not protection.
- **Every reservation write has to go through the service.** A reservation inserted directly in the database doesn't decrement the counters.
- **Changing the size of the fleet is not supported.** A change to `car_count` must also change `available_cars` on the open days; no code does that today.
- **A day missing from the calendar is reported as "no car available"**, the same as a fully booked day.
- **No payment or temporary hold.** A reservation is confirmed straight away. A hold would be a `HELD` status with an `expires_at`, plus a job that returns the cars of expired holds.
- **Idempotency keys never expire, and past calendar days are never deleted.**
- **PostgreSQL is configured but not tested.** No Testcontainers run against it, and no test forces a deadlock; ordered locking rests on the `order by … for update` statement.
- **Lock waits are capped at 5 seconds** (`lock_timeout` on PostgreSQL, `LOCK_TIMEOUT` on H2) and then return `503`; the server does not retry them. A test proves this on H2; on PostgreSQL it is configured only.
- **Out of scope:** pricing, the pick-up and return handover (assigning a specific car, recording that it left or came back), changing a reservation, listing reservations, monitoring.
