# Car Rental System

A simulated car rental system. You reserve a car of a given type (Sedan, SUV, Van) from a start date and time for a number of days. Each type has a limited number of cars.

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

At startup, Flyway seeds 3 car types: 3 sedans, 2 SUVs and 1 van (`car_type.car_count`). A calendar job then opens every bookable day for every type, with all of that type's cars free. It runs before the server accepts requests, and again on `reservation.calendar-cron` (daily at midnight UTC).

## Usage

Interactive API docs (Swagger UI): <http://localhost:8080/swagger-ui.html>. The OpenAPI spec is at `/v3/api-docs`.

Reserve a car. The `Idempotency-Key` header is required. A retry with the same key and the same body returns the same `201` response.

```bash
curl -i -X POST localhost:8080/reservations \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: 7f3c2a' \
  -d '{"carType":"SUV","startsAt":"2026-12-01T10:00:00Z","days":3,"customerId":"customer-1"}'
```

```json
{"id":"…","carType":"SUV","startsAt":"2026-12-01T10:00:00Z","endsAt":"2026-12-04T10:00:00Z","status":"ACTIVE"}
```

Cancel a reservation before its start. Cancelling again returns `204` too.

```bash
curl -i -X DELETE 'localhost:8080/reservations/{id}?customerId=customer-1'
```

Errors are returned as `application/problem+json`:

| Status | When |
|---|---|
| 400 | invalid body, missing `Idempotency-Key`, start in the past or beyond the booking horizon, days outside 1–30 |
| 404 | unknown car type; reservation not found, or it belongs to another customer |
| 409 | no car of that type is free for the whole period; the reservation has already started (cancel) |
| 422 | the `Idempotency-Key` was already used for a different request |
| 503 | lock timeout or deadlock; retry with the same `Idempotency-Key` after `Retry-After` seconds |
| 500 | anything unexpected; logged with its stack trace, and the response carries only a generic message |

## Assumptions

- **You reserve a type, not a specific car**, the way rental companies sell a category. The specific car is assigned at pick-up, which is out of scope.
- **Availability is counted per calendar day (UTC).** A reservation occupies every day it touches. One that starts Jan 1 at 10:00 for 3 days occupies Jan 1–4, so a car returned on a day can go out again only the next day. It is conservative: a car can never be double-booked, but there's no same-day turnaround.
- **The period is `[start, start + days × 24h)`**, stored to the microsecond.
- **A start may be anywhere from now to 90 days ahead.** A reservation lasts 1 to 30 days. Both limits are set in `application.yml` (`reservation.*`) and bound by `ReservationProperties`, so the domain rules stay free of Spring.
- **Car types are data, not code.** Adding a type means writing a migration; no Java changes.
- **Every reservation carries an idempotency key, unique per customer.** A retry with the same key and the same request returns the original reservation, even a cancelled one. Reusing the key for a different request is rejected.
- **Only the owner can cancel, and only before the start.** After the start, the cars are in use.

## Trade-offs

- **An inventory calendar with atomic counters** (`car_availability`: free cars per type per day). A reservation takes one car from each day it occupies in a single conditional `UPDATE … set free_cars = free_cars - 1 where … and free_cars > 0`.
  - If fewer days were updated than the period covers, the transaction rolls back.
  - The check and the decrement are one statement, so no other request can slip in between them.
  - `check (free_cars >= 0)` keeps the count from ever going negative.
  - Only reservations that share a day of the same type wait for each other, and only for that row lock. Different days and different types run in parallel, across threads and app instances.
- **The days are locked in date order** (`select … order by calendar_day for update`) before they are updated. Overlapping reservations therefore can't deadlock each other, however the database scans the table.
- **The reservation row is inserted before the counters are decremented.** Two concurrent retries with the same key then meet at the unique key. The second one waits, fails on the key, and reads the first one's reservation in a new transaction.
- **`reserve` runs in its own transaction and retries once after a duplicate idempotency key** (`@Transactional(propagation = REQUIRES_NEW)` plus Spring's `@Retryable`). The retry wraps the transaction, so the second attempt starts fresh and finds the reservation the concurrent request committed. A caller's transaction or isolation level can't change the outcome.
- **Cancel changes the status and gives the cars back in one transaction.** Only the request that actually moved the status from `ACTIVE` to `CANCELLED` releases the cars, so concurrent cancels release them once. The row and its idempotency key stay, so a late retry can't recreate the reservation.
- **The domain classes are the JPA entities.** That means less code and no mappers, but the domain carries JPA annotations. The counters use plain SQL (`JdbcClient`), because the logic is one statement each.

## Limitations

- **No security.** There is no authentication or authorization. `customerId` is trusted as the caller sends it, so the ownership check on cancel is a business rule, not protection.
- **Every reservation write has to go through the service.** A reservation inserted directly in the database doesn't decrement the counters.
- **Changing the size of the fleet is not supported.** A change to `car_count` must also change `free_cars` on the open days; no code does that today.
- **A day missing from the calendar is reported as "no car available"**, the same as a fully booked day.
- **No payment or temporary hold.** A reservation is confirmed straight away. A hold would be a `HELD` status with an `expires_at`, plus a job that returns the cars of expired holds.
- **Idempotency keys never expire, and past calendar days are never deleted.**
- **PostgreSQL is configured but not tested.** No Testcontainers run against it, and no test forces a deadlock; ordered locking rests on the `order by … for update` statement.
- **Lock timeouts are returned as `503`, not retried by the server.**
- **Out of scope:** pricing, pick-up and return, changing a reservation, listing reservations, monitoring.
