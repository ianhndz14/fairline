-- One soccer match. Final score stays NULL until the match is played.
CREATE TABLE event (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kalshi_event_ticker VARCHAR(64) UNIQUE,
    home_team           VARCHAR(64) NOT NULL,
    away_team           VARCHAR(64) NOT NULL,
    kickoff             TIMESTAMPTZ NOT NULL,
    home_goals          INT CHECK (home_goals >= 0),
    away_goals          INT CHECK (away_goals >= 0),
    CONSTRAINT event_distinct_teams CHECK (home_team <> away_team),
    CONSTRAINT event_score_complete CHECK ((home_goals IS NULL) = (away_goals IS NULL)),
    CONSTRAINT event_unique_fixture UNIQUE (home_team, away_team, kickoff)
);

-- One tradable 1X2 outcome of an event (Kalshi lists three binary markets per match).
CREATE TABLE market (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id      BIGINT      NOT NULL REFERENCES event (id) ON DELETE CASCADE,
    outcome       VARCHAR(4)  NOT NULL CHECK (outcome IN ('HOME', 'DRAW', 'AWAY')),
    kalshi_ticker VARCHAR(64) UNIQUE,
    CONSTRAINT market_one_per_outcome UNIQUE (event_id, outcome)
);

-- Market price at a point in time, in dollars per $1 contract (= implied probability).
CREATE TABLE price_snapshot (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    market_id   BIGINT       NOT NULL REFERENCES market (id) ON DELETE CASCADE,
    captured_at TIMESTAMPTZ  NOT NULL,
    yes_bid     NUMERIC(5, 4) NOT NULL CHECK (yes_bid BETWEEN 0 AND 1),
    yes_ask     NUMERIC(5, 4) NOT NULL CHECK (yes_ask BETWEEN 0 AND 1),
    CONSTRAINT price_snapshot_bid_le_ask CHECK (yes_bid <= yes_ask),
    CONSTRAINT price_snapshot_unique_time UNIQUE (market_id, captured_at)
);

-- Poisson model output for an event at a point in time.
CREATE TABLE model_estimate (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id    BIGINT           NOT NULL REFERENCES event (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ      NOT NULL,
    home_lambda DOUBLE PRECISION NOT NULL CHECK (home_lambda >= 0),
    away_lambda DOUBLE PRECISION NOT NULL CHECK (away_lambda >= 0),
    p_home      DOUBLE PRECISION NOT NULL CHECK (p_home BETWEEN 0 AND 1),
    p_draw      DOUBLE PRECISION NOT NULL CHECK (p_draw BETWEEN 0 AND 1),
    p_away      DOUBLE PRECISION NOT NULL CHECK (p_away BETWEEN 0 AND 1)
);

-- A detected opportunity: model and (normalized) market disagree by more than the threshold.
CREATE TABLE edge_log (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    market_id         BIGINT           NOT NULL REFERENCES market (id) ON DELETE CASCADE,
    model_estimate_id BIGINT           NOT NULL REFERENCES model_estimate (id) ON DELETE CASCADE,
    detected_at       TIMESTAMPTZ      NOT NULL,
    model_prob        DOUBLE PRECISION NOT NULL CHECK (model_prob BETWEEN 0 AND 1),
    market_prob       DOUBLE PRECISION NOT NULL CHECK (market_prob BETWEEN 0 AND 1),
    edge              DOUBLE PRECISION NOT NULL
);

-- Foreign keys aren't indexed automatically in PostgreSQL; these back the history/lookup queries.
-- (price_snapshot and market are already covered by their UNIQUE constraints.)
CREATE INDEX model_estimate_event_time ON model_estimate (event_id, created_at);
CREATE INDEX edge_log_market ON edge_log (market_id);
CREATE INDEX edge_log_estimate ON edge_log (model_estimate_id);
