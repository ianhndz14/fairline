# Fairline

A full-stack probability modeling tool (Java/Spring Boot, React, PostgreSQL) that estimates sports outcomes with a Poisson distribution model built from scratch, and compares those estimates against live prediction-market prices to flag pricing inefficiencies.

## How it works

1. **Model** — A Poisson model estimates the probability of every possible outcome of an event from each team's historical scoring averages. It's implemented in plain Java with no external ML libraries, and the probabilities are normalized so they sum to 1.
2. **Market** — Prices for the same event are pulled from a prediction market ([Kalshi](https://kalshi.com)), with manual/CSV upload as a fallback, and stored as timestamped snapshots.
3. **Edge** — When the gap between the model's probability and the market's implied probability exceeds a configurable threshold, Fairline flags it as a potential +EV pricing inefficiency.

## MVP scope

- **Sport:** Soccer — English Premier League
- **Market:** 1X2 match result (home win / draw / away win)
- **Out of scope for now:** other leagues, totals (over/under), handicaps, player props. These can be added after the MVP, since the same Poisson scoreline grid covers them.

## Tech stack

| Layer    | Technologies                                        |
| -------- | --------------------------------------------------- |
| Backend  | Java 17, Spring Boot, Spring Data JPA, Flyway        |
| Database | PostgreSQL                                          |
| Frontend | React, TypeScript, Recharts                         |
| Testing  | JUnit 5, Spring Boot Test / MockMvc, Jest, React Testing Library |
| DevOps   | Docker, GitHub Actions                              |

## Project structure

```
fairline/
├── backend/    # Spring Boot API + Poisson probability engine
├── frontend/   # React + TypeScript web app
└── docs/       # Design notes (data sources, wireframes)
```

## Roadmap

Phases are ordered because each builds on the previous one. Phase 5 is optional.

### Phase 0 — Planning & setup
- [x] Pick one sport and market type for the MVP: Premier League, 1X2 match result
- [x] Review the Kalshi API docs and define a fallback: public API confirmed, manual/CSV upload kept as plan B ([findings](docs/data-sources.md))
- [ ] Wireframe 3 screens: calculator, opportunities dashboard, history
- [x] Create the repository with an initial README and folder structure

### Phase 1 — Probability engine (Poisson)
- [ ] Implement the Poisson model in plain Java from each team's average goals/points
- [ ] Normalize outcome probabilities so they sum to 1
- [ ] Unit test with JUnit 5 against hand-calculated cases

### Phase 2 — Data model & persistence
- [ ] Entities: `Event`, `Market`, `ModelEstimate`, `PriceSnapshot`, `EdgeLog`
- [ ] Relationships and constraints in PostgreSQL via Spring Data JPA
- [ ] Versioned schema migrations with Flyway

### Phase 3 — REST API & market data
- [ ] Endpoints: load an event with stats, get the model estimate, list detected opportunities
- [ ] Price ingestion (Kalshi API, or manual upload as fallback) stored as timestamped snapshots
- [ ] Comparison logic: record an opportunity when model vs. market exceeds a configurable threshold

### Phase 4 — React frontend
- [ ] Interactive calculator: enter match stats → instant estimated probabilities
- [ ] Dashboard of +EV opportunities, sorted by edge size
- [ ] Chart of model probability vs. market price over time (Recharts)

### Phase 5 — Authentication *(optional)*
- [ ] Basic login with Spring Security + JWT
- [ ] Per-user watchlists and saved preferences

### Phase 6 — Testing & quality
- [ ] API tests for critical endpoints (Spring Boot Test / MockMvc)
- [ ] Component tests for key frontend views (Jest + React Testing Library)
- [ ] Consistent linting and formatting across backend and frontend

### Phase 7 — CI/CD & deployment
- [ ] GitHub Actions running all tests on every push
- [ ] Deploy backend + database (Render or Railway) and frontend (Vercel)
- [ ] Environment variables and secrets kept out of the codebase

### Phase 8 — Polish
- [ ] README with screenshots or a short demo GIF
- [ ] Real metrics: events analyzed, model accuracy vs. final results
- [ ] Link to the live demo
