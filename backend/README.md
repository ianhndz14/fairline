# Fairline — Backend

Java 21 + Spring Boot 4 service that powers Fairline.

## How it works

1. **Results** — every 6 hours, `ResultsImporter` loads the current and previous Premier League seasons from football-data.co.uk.
2. **Team strength** — `TeamStats` turns the last 365 days of results into home/away attack and defence rates (blended with 5 league-average matches) and derives each side's expected goals (λ).
3. **Model** — `PoissonModel` (plain Java, no libraries) turns the two λ values into a normalized scoreline grid and home/draw/away probabilities.
4. **Prices** — every 10 minutes, `PriceIngestion` pulls open EPL markets from Kalshi's public API and stores bid/ask snapshots for matches that haven't kicked off.
5. **Edges** — `EdgeDetector` normalizes the market's mid prices to sum to 1, compares them with the model, and logs outcomes the model rates at least `fairline.edge.threshold` (default 3 points) higher than the market.

## Run it

Requires Java 21 and PostgreSQL. The Maven Wrapper downloads Maven on first run.

1. Create the app's database once, as the `postgres` superuser:

   ```sql
   CREATE ROLE fairline LOGIN;
   \password fairline
   CREATE DATABASE fairline OWNER fairline;
   ```

2. Copy `.env.example` to `.env` and set `DB_PASSWORD` (`.env` is git-ignored).
3. Start the app, or run the tests. Flyway creates the tables on startup.

   ```bash
   ./mvnw spring-boot:run   # macOS / Linux / Git Bash (mvnw.cmd on Windows)
   ./mvnw test
   ```

The API is then at `http://localhost:8080`. Data appears within about 30 seconds of startup.

## API

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/api/teams` | Current teams with their home/away scoring and conceding rates |
| `GET` | `/api/estimate?home=&away=[&homeLambda=&awayLambda=]` | Model λ, 1X2 probabilities and scoreline grid; normalized Kalshi prices if the match is listed |
| `GET` | `/api/opportunities[?minEdge=0.05]` | Upcoming outcomes where model − market ≥ minEdge, biggest first, plus the last price update time |
| `POST` | `/api/events` | Add a match manually: `{"homeTeam", "awayTeam", "kickoff"}` |
| `POST` | `/api/events/{id}/prices` | Enter prices manually: `{"HOME": {"bid", "ask"}, "DRAW": {...}, "AWAY": {...}}` |

Errors are returned as [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details (`400` invalid input, `404` unknown event, `409` duplicate, `503` no results imported yet).

## Code layout

```
src/main/java/io/github/ianhndz14/fairline/
├── engine/   Poisson model (framework-free)
├── stats/    Results import and team strength
├── market/   Kalshi client, price ingestion, edge detection, API use cases
├── domain/   JPA entities and repositories
└── api/      REST controller
src/main/resources/db/migration/   Flyway SQL migrations
```
