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
