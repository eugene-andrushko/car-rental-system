# Car Rental System

A simulated car rental system. You reserve a car of a given type (Sedan, SUV, Van) from a start date and time for a number of days. Each type has a limited number of cars.

## Requirements

- Java 25

## Run

```bash
./mvnw spring-boot:run
```

The app starts on an in-memory H2 database. To run against PostgreSQL, set `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`, then:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres
```

## Test

```bash
./mvnw test
```

## Seed data

At startup, Flyway seeds 3 car types: 3 sedans, 2 SUVs and 1 van (`car_type.car_count`). A calendar job then opens every bookable day for every type, with all of that type's cars free. It runs at startup and then daily at midnight UTC.

## Assumptions

- **You reserve a type, not a specific car**, the way rental companies sell a category. The specific car is assigned at pick-up, which is out of scope.
- **Availability is counted per calendar day (UTC).** A reservation occupies every day it touches. One that starts Jan 1 at 10:00 for 3 days occupies Jan 1–4, so a car returned on a day can go out again only the next day. It is conservative: a car can never be double-booked, but there's no same-day turnaround.
- **The period is `[start, start + days × 24h)`**, stored to the microsecond.
- **A start may be anywhere from now to 90 days ahead.** A reservation lasts 1 to 30 days. Both limits are set in `application.yml` (`reservation.*`).
- **Car types are data, not code.** Adding a type means writing a migration; no Java changes.
- **Every reservation carries an idempotency key, unique per customer.** A retry with the same key and the same request returns the original reservation, even a cancelled one. Reusing the key for a different request is rejected.

## Trade-offs

- **An inventory calendar with atomic counters** (`car_availability`: free cars per type per day). A reservation takes one car from each day it occupies in a single conditional `UPDATE … set free_cars = free_cars - 1 where … and free_cars > 0`.
  - If fewer days were updated than the period covers, the transaction rolls back.
  - The check and the decrement are one statement, so no other request can slip in between them.
  - `check (free_cars >= 0)` keeps the count from ever going negative.
  - Only reservations that share a day of the same type wait for each other, and only for the length of that one statement's row lock. Different days and different types run in parallel, across threads and app instances.
  - Under a snapshot isolation level (`REPEATABLE_READ`), a competing transaction fails instead of overbooking.
- **The reservation row is inserted before the counters are decremented.** Two concurrent retries with the same key then meet at the unique key. The second one waits, fails on the key, and returns the first reservation.
- **Cancel changes the status and gives the cars back in one transaction.** Only the request that actually moved the status from `ACTIVE` to `CANCELLED` releases the cars, so concurrent cancels release them once. The row and its idempotency key stay, so a late retry can't recreate the reservation.
- **The domain classes are the JPA entities.** That means less code and no mappers, but the domain carries JPA annotations. The counters use plain SQL (`JdbcClient`), because the logic is one statement each.

## Limitations

- **No security.** There is no authentication or authorization. `customerId` is trusted as the caller sends it, so the ownership check on cancel is a business rule, not protection.
- **Every reservation write has to go through the service.** A reservation inserted directly in the database doesn't decrement the counters.
- **Changing the size of the fleet is not supported.** A change to `car_count` must also change `free_cars` on the open days; no code does that today.
- **No payment or temporary hold.** A reservation is confirmed straight away. A hold would be a `HELD` status with an `expires_at`, plus a job that returns the cars of expired holds.
- **Idempotency keys never expire.**
- **PostgreSQL is configured but not tested.** No Testcontainers run against it.
- **Deadlocks, lock timeouts and serialization failures aren't retried.** Overlapping multi-day updates lock rows in primary-key order, so they shouldn't deadlock, but nothing guarantees it.
- **Out of scope:** pricing, pick-up and return, changing a reservation, listing reservations, monitoring.
