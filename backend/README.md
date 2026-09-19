# Fairline — Backend

Java 21 + Spring Boot 4 service that powers Fairline.

## Local database

Requires PostgreSQL (tested on 18). Create the app's user and database once, as the `postgres` superuser:

```sql
CREATE ROLE fairline LOGIN;
\password fairline
CREATE DATABASE fairline OWNER fairline;
```

Then copy `.env.example` to `.env` and set `DB_PASSWORD`. `.env` is git-ignored. Flyway creates the tables from `src/main/resources/db/migration` on startup.

## Run the tests

Requires Java 21 and the database above. The Maven Wrapper downloads Maven on first run.

```bash
./mvnw test        # macOS / Linux / Git Bash
mvnw.cmd test      # Windows PowerShell / cmd
```

This folder will contain:

- **Probability engine** — a Poisson model written in plain Java (no ML libraries) that estimates the probability of each possible outcome of an event, normalized so all outcomes sum to 1.
- **Persistence** — PostgreSQL schema managed with Spring Data JPA and Flyway migrations (`Event`, `Market`, `ModelEstimate`, `PriceSnapshot`, `EdgeLog`).
- **REST API** — endpoints to load events, get model estimates, ingest market prices, and list detected pricing opportunities.
- **Tests** — JUnit 5 unit tests for the engine and Spring Boot / MockMvc tests for the API.
