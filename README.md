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
./mvnw verify
```

- **Unit tests** cover the domain rules: period validation, occupied days, and matching a retry to its reservation.
- **The car limit is enforced by the database** (see Trade-offs), so `@SpringBootTest` integration tests on H2 prove it.
- **The integration tests also force concurrent interleavings:** a request is paused while it holds its locks, and the test checks who has to wait and what the other request gets.

## Code

- `fleet`: the cars and how many are reserved on each day. `domain` (car type, occupied days), `persistence` (car types with JPA, the day counters with SQL), `application` (fleet size change), `web` (fleet endpoint).
- `reservation`: customer bookings, which depend on `fleet`. `domain` (rules, period, reservation), `application` (reserve and cancel, the calendar job that opens bookable days), `persistence` (reservations with JPA), `web` (reservation endpoints), `config` (booking limits from `application.yml`).
- `web.ApiExceptionHandler` maps every error to a status; `config` holds the clock and OpenAPI beans.

## Seed data

Flyway seeds 3 car types: 3 sedans, 2 SUVs and 1 van (`car_type.car_count`).

## Usage

Interactive API docs (Swagger UI): <http://localhost:8080/swagger-ui.html>. The OpenAPI spec is at `/v3/api-docs`.

Reserve a car. `carType` is case-insensitive. The `Idempotency-Key` header is required. A retry with the same key and the same body returns the same `201` response. A request sent while another one with the same key is still being processed gets `409`; retrying it then returns the reservation.

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

Change how many cars of a type the fleet has (an extra the spec didn't ask for). The new count applies at once to every open day.

```bash
curl -i -X PUT localhost:8080/car-types/SEDAN/car-count \
  -H 'Content-Type: application/json' -d '{"carCount":4}'
```

```json
{"name":"SEDAN","carCount":4}
```

Errors are returned as `application/problem+json`:

| Status | When |
|---|---|
| 400 | invalid body or `carCount`, missing `Idempotency-Key`, `customerId` or `Idempotency-Key` over 100 characters, pick-up in the past or beyond the booking horizon, days outside 1–30 |
| 404 | unknown car type; reservation not found, or owned by another customer |
| 409 | no car available for the whole period; same `Idempotency-Key` in progress; pick-up time passed (cancel); more cars already reserved than the new `carCount` (fleet change) |
| 422 | `Idempotency-Key` already used for a different request |
| 503 | temporary database failure (lock wait over 5 seconds, deadlock, query timeout); retry with the same `Idempotency-Key` after `Retry-After` seconds |
| 500 | anything unexpected; logged with its stack trace, the response carries a generic message |

## Assumptions

- **You reserve a type, not a specific car**, the way rental companies sell a category. The specific car is assigned at pick-up, which is out of scope.
- **Availability is counted per calendar day (UTC).** A reservation occupies every day it touches. One picked up Jan 1 at 10:00 for 3 days is returned Jan 4 at 10:00 and occupies Jan 1–4, so a car returned on a day can go out again only the next day. It is conservative: a car can never be double-booked, but there's no same-day turnaround. Real branches count days in their local time; there are no locations here, so a 23:00 pick-up in New York (04:00 UTC) falls on the next day.
- **`returnAt` is the exclusive end of the period**, stored to the microsecond.
- **A pick-up may be anywhere from now to 90 days ahead.** A reservation lasts 1 to 30 days. Both limits are in `application.yml` (`reservation.*`), bound by `ReservationProperties`, so the domain rules stay free of Spring.
- **Every reservation carries an idempotency key, unique per customer.** A retry with the same key and the same request returns the original reservation, even a cancelled one. Reusing the key for a different request is rejected.
- **Only the owner can cancel, and only before the pick-up time.** After it, the car is treated as in use.

## Trade-offs

- **An inventory calendar with atomic counters** (`car_availability`: reserved cars per type per day). A reservation reserves one car on each day it occupies in a single conditional `UPDATE … set reserved_cars = reserved_cars + 1 where … and reserved_cars < car_type.car_count`.
  - The fleet size lives only in `car_type.car_count`; available cars on a day are `car_count - reserved_cars`.
  - The check and the increment are one statement, so no other request can slip in between them. If fewer days were updated than the period covers, the transaction rolls back.
  - Only reservations that share a day of the same type wait for each other. Different days and different types run in parallel, across threads and app instances.
- **The calendar is opened ahead of time.** A job creates each type's day rows before the server accepts requests and daily at midnight UTC (`reservation.calendar-cron`), up to 7 spare days past the latest possible return day, so a week of missed runs leaves no bookable day closed. Rows exist before anyone reserves, so a fleet change can lock every day it needs to check.
- **The days are locked in date order** (`select … order by calendar_day for update`) before they are updated, so overlapping requests can't deadlock each other.
- **The reservation row is inserted before the cars are reserved on its days.** Two concurrent requests with the same key then meet at the unique key: the second waits for the first, fails on the key, and gets `409`, as the IETF Idempotency-Key draft and Stripe's API do. The client's retry returns the reservation.
- **Cancel changes the status and gives the cars back in one transaction.** Only the request that moved the status from `CONFIRMED` to `CANCELLED` releases the cars, so concurrent cancels release them once. The row and its key stay, so a late retry can't recreate the reservation.
- **A fleet change only sets `car_type.car_count`.** It locks the car type, then the type's open days in date order, and rejects the change with `409` if more cars are reserved on any day than the new count. A reservation in progress finishes first; a later one waits and checks against the new count.
- **The calendar job locks the car types before it opens a day**, so a day it opens waits for a fleet change to commit, and two app instances don't insert the same day.
- **The domain classes are the JPA entities.** Less code and no mappers, but the domain carries JPA annotations. The counters use plain SQL (`JdbcClient`), because each is one statement.

## Limitations

- **No security.** No authentication or authorization, including on the fleet endpoint. `customerId` is trusted as sent, so the ownership check on cancel is a business rule, not protection.
- **Every reservation write has to go through the service.** A reservation inserted directly in the database doesn't count as reserved on its days.
- **A new car type is added with a migration**, and becomes bookable after the next calendar run (startup or midnight UTC). Until then, like any day missing from the calendar, it gets "no car available". The fleet endpoint changes only the count of an existing type.
- **One type on one day is one row.** Reservations for a popular type and day queue on it; very high demand would need the count split across several rows or requests put in a queue.
- **No payment or temporary hold.** A reservation is confirmed straight away. A hold would be a `HELD` status with an `expires_at`, plus a job that returns the cars of expired holds.
- **Idempotency keys never expire, and past calendar days are never deleted.**
- **PostgreSQL is configured but not tested.** No Testcontainers run against it, and no test forces a deadlock; ordered locking rests on the `order by … for update` statement.
- **Lock waits are capped at 5 seconds** (`lock_timeout` on PostgreSQL, `LOCK_TIMEOUT` on H2) and then return `503`; the server does not retry. A test proves this on H2; on PostgreSQL it is configured only.
- **Out of scope:** pricing, the pick-up and return handover (assigning a specific car, recording that it left or came back), changing a reservation, reading or listing reservations (so the `201` carries no `Location` header), monitoring.
