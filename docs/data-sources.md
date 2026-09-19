# Data sources

Findings from the Phase 0 review (September 2026). Fairline needs two kinds of data: **match results** to fit the Poisson model, and **market prices** to compare against.

## Market prices — Kalshi

Kalshi's market data endpoints are **public: no account or API key needed** to read them.

- **Base URL:** `https://external-api.kalshi.com/trade-api/v2`
- **Hierarchy:** series → event → market
  - **Series** `KXEPLGAME`: all English Premier League game-result markets
  - **Event** = one match, e.g. `KXEPLGAME-26SEP20FULMUN` (Fulham vs Manchester United, Sep 20 2026)
  - **Markets** = three binary (yes/no) contracts per event, one per 1X2 outcome:
    `...-FUL` (home win), `...-TIE` (draw), `...-MUN` (away win)
- **Kickoff:** `occurrence_datetime` is **kickoff + 3 hours** (checked against football-data's recorded kickoff for Arsenal vs Coventry, Aug 21 2026). Event titles are always `"Home vs Away"`.
- **Resolution:** result after 90 minutes plus stoppage time (no extra time or penalties), which matches what the Poisson model predicts.

### Endpoints

| Purpose | Endpoint |
| --- | --- |
| List EPL markets | `GET /markets?series_ticker=KXEPLGAME&status=open` |
| One match's markets | `GET /markets?event_ticker={event}` |
| Price history | `GET /series/KXEPLGAME/markets/{ticker}/candlesticks?start_ts=&end_ts=&period_interval=60` |

Useful market fields: `yes_bid_dollars`, `yes_ask_dollars`, `last_price_dollars`, `volume_fp`, `occurrence_datetime` (kickoff), `status`, `result`. Prices are **decimal strings** in dollars (`"0.4800"` = 48% implied probability), so parse them as `BigDecimal`, not `double`.

Candlesticks return OHLC for trade price, bid and ask per period (`period_interval` in minutes: 1, 60 or 1440).

### Market prices don't sum to 1

The three outcomes of a match are priced separately, and their prices add up to slightly more than 1. That extra is the market's margin. Example (Fulham vs Man Utd, yes ask): 0.28 + 0.26 + 0.48 = **1.02**.

To compare against the model, take each outcome's mid price `(bid + ask) / 2` and **normalize the three so they sum to 1**. This is the same normalization the model's own probabilities go through.

### Plan B

Keep a manual/CSV price upload path. It covers API changes or outages, lets us demo historical matches, and makes tests run without network access.

## Match results — football-data.co.uk

Free Premier League CSVs for every season since 1993/94, updated during the season.

- **URL pattern:** `https://football-data.co.uk/mmz4281/{season}/E0.csv` (the `www.` address redirects here), where `{season}` is e.g. `2627` for 2026/27 and `E0` is the Premier League
- **Columns we need:** `Date` (dd/MM/yyyy), `HomeTeam`, `AwayTeam`, `FTHG` (full-time home goals), `FTAG` (full-time away goals), `FTR` (H/D/A)
- **Parsing notes:** the file starts with a UTF-8 BOM (an invisible marker at the very start of the file), so strip it before reading the first column name. Team names differ from Kalshi's (e.g. "Man United" vs "Manchester United"), so we'll need a small name-mapping table.
