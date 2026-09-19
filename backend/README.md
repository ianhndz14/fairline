# Fairline — Backend

Java 17 + Spring Boot service that powers Fairline.

This folder will contain:

- **Probability engine** — a Poisson model written in plain Java (no ML libraries) that estimates the probability of each possible outcome of an event, normalized so all outcomes sum to 1.
- **Persistence** — PostgreSQL schema managed with Spring Data JPA and Flyway migrations (`Event`, `Market`, `ModelEstimate`, `PriceSnapshot`, `EdgeLog`).
- **REST API** — endpoints to load events, get model estimates, ingest market prices, and list detected pricing opportunities.
- **Tests** — JUnit 5 unit tests for the engine and Spring Boot / MockMvc tests for the API.

> Not scaffolded yet — see the [Roadmap](../README.md#roadmap).
