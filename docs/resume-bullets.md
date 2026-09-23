# Resume bullets

Drafts for the resume and LinkedIn. Numbers marked `[fill]` come from the running app — see "How to get the numbers"
at the bottom. Keep only the 2–3 that fit the role you're applying to.

## Full-stack framing

> Built **Fairline**, a full-stack probability tool (Java 21 / Spring Boot 4, React 19 / TypeScript, PostgreSQL) that
> estimates Premier League match outcomes with a from-scratch Poisson model and compares them against live Kalshi
> prediction-market prices, flagging outcomes the market underprices by more than a configurable threshold.

## Modelling and testing framing

> Implemented a Poisson probability engine in plain Java (no ML libraries): expected goals per side from a year of
> results shrunk toward the league average, a normalized 11×11 scoreline grid, and 1X2 probabilities — validated by
> **49 automated tests** (JUnit 5, MockMvc, Vitest) against hand-calculated benchmarks.

## Data pipeline framing

> Automated the data pipeline with scheduled jobs: 380+ historical results per season imported from CSV, Kalshi's
> public market API polled every 10 minutes into timestamped price snapshots, and normalized market probabilities
> compared against the model to log opportunities — **420+ matches imported** across two seasons.

## CI/CD framing

> Set up CI/CD with GitHub Actions running on every push — strict compilation (warnings as errors), 37 backend tests
> against a PostgreSQL service container, a Docker image build, and frontend lint/format/tests/build — deployed at
> **[fill: live URL]**.

## Honest-modelling framing (good interview bait)

> Measured the model against the market instead of assuming it beats it: each flagged outcome is judged once by its
> closing edge, and the app reports hit rate next to the market's own implied probability — currently
> **30% hit rate over 10 settled outcomes**, against **22.6%** implied by the market (too small a sample to claim
> an edge, which the app says on screen).

## How to get the numbers

With the app running (see the root README):

| Number | Where |
| --- | --- |
| Matches analyzed | `GET /api/events` length, or `SELECT count(*) FROM event` |
| Hit rate, settled count, market/model expectation | `GET /api/track-record`, or the History screen's stats strip |
| Live URL | your Vercel deployment |

Only claim the hit rate once `settled` is comfortably above ~30; below that it says more about luck than skill, which
is exactly what the History screen warns about.
