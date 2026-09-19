# Wireframes

Low-fidelity layouts for the three MVP screens. They define **what each screen shows and which data it needs**, not the visual design. Each screen lists the API data it depends on, which drives the Phase 3 endpoints.

## 1. Calculator

Pick two teams (or type expected goals directly) and see the model's 1X2 probabilities instantly.

```
┌───────────────────────────────────────────────────────────────────┐
│ FAIRLINE           [ Calculator ]   Opportunities   History       │
├───────────────────────────────────────────────────────────────────┤
│                                                                   │
│   Home team  [ Fulham            ▼ ]   Away team [ Man United ▼ ] │
│                                                                   │
│   Expected goals (λ)   Home [ 1.21 ]   Away [ 1.48 ]              │
│   (auto-filled from team stats, editable)                         │
│                                                                   │
│  ┌──────────────┬──────────────┬──────────────┐                   │
│  │  HOME WIN    │    DRAW      │   AWAY WIN   │                   │
│  │   31.8 %     │   26.1 %     │   42.1 %     │  ← model          │
│  │   27.5 %     │   25.4 %     │   47.1 %     │  ← Kalshi (norm.) │
│  │   +4.3 pts ▲ │   +0.7 pts   │   −5.0 pts   │  ← edge           │
│  └──────────────┴──────────────┴──────────────┘                   │
│                                                                   │
│   Scoreline probabilities            Home goals →                 │
│              0     1     2     3     4+                           │
│   Away   0  [██]  [██]  [█ ]  [  ]  [  ]                          │
│   goals  1  [██]  [██]  [█ ]  [  ]  [  ]    (heatmap, hover       │
│     ↓    2  [█ ]  [█ ]  [  ]  [  ]  [  ]     shows exact %)       │
│          3  [  ]  [  ]  [  ]  [  ]  [  ]                          │
│                                                                   │
└───────────────────────────────────────────────────────────────────┘
```

- Market row and edge row appear only if Kalshi has an open market for that match.
- Edge = model % − normalized market %. Positive edge (▲) means the model rates the outcome higher than the market does.

**Needs from the API:** team list · team expected goals · model estimate for (home, away, λs) incl. scoreline grid · latest normalized market prices for the match (if any)

## 2. Opportunities dashboard

Upcoming matches where the model disagrees with the market by more than the threshold, biggest edge first.

```
┌────────────────────────────────────────────────────────────────────────┐
│ FAIRLINE             Calculator  [ Opportunities ]  History            │
├────────────────────────────────────────────────────────────────────────┤
│  Min edge [ 3.0 ] pts      Showing 4 of 10 matches    ↻ 5 min ago      │
├──────────────┬─────────────────────┬─────────┬────────┬────────┬───────┤
│ Kickoff      │ Match               │ Outcome │ Model  │ Market │ Edge ▼│
├──────────────┼─────────────────────┼─────────┼────────┼────────┼───────┤
│ Sat 19 15:00 │ Everton v Brighton  │ Draw    │ 30.2 % │ 24.1 % │ +6.1  │
│ Sun 20 18:30 │ Fulham v Man Utd    │ Home    │ 31.8 % │ 27.5 % │ +4.3  │
│ Sat 19 12:30 │ Spurs v Arsenal     │ Away    │ 44.0 % │ 40.2 % │ +3.8  │
│ Mon 21 20:00 │ Leeds v Burnley     │ Home    │ 51.5 % │ 48.4 % │ +3.1  │
├──────────────┴─────────────────────┴─────────┴────────┴────────┴───────┤
│  Click a row → History view for that match                             │
└────────────────────────────────────────────────────────────────────────┘
```

- Threshold is adjustable; the default comes from backend config.
- "↻ 5 min ago" shows when market prices were last fetched, so stale data is obvious.

**Needs from the API:** list of opportunities (match, kickoff, outcome, model %, market %, edge) filtered by min edge · last price-ingestion time

## 3. History

How the model and the market moved for one match, and how past flagged opportunities turned out.

```
┌───────────────────────────────────────────────────────────────────┐
│ FAIRLINE             Calculator   Opportunities  [ History ]      │
├───────────────────────────────────────────────────────────────────┤
│  Match [ Fulham v Man United (Sep 20) ▼ ]                         │
│  Outcome  ( Home )  ( Draw )  (•Away )                            │
│                                                                   │
│   50% ┤                               ___---- market              │
│       │                  ____----‾‾‾‾                             │
│   45% ┤ ----‾‾‾‾‾‾‾‾‾‾‾‾                                          │
│       │ ═══════════════════════════════════ model                 │
│   40% ┤                                                           │
│       └──────┬──────┬──────┬──────┬──────┬──                      │
│            Sep 15  Sep 16  Sep 17  Sep 18  Sep 19                 │
│                                                                   │
├───────────────────────────────────────────────────────────────────┤
│  Past flagged opportunities                                       │
│  Date     Match                Outcome  Edge   Result   Hit?      │
│  Sep 14   Chelsea v Wolves     Draw     +5.2   1–1      ✓         │
│  Sep 13   Villa v Newcastle    Home     +3.4   0–2      ✗         │
│  ...                                                              │
│  Hit rate: 41% (7 / 17)   ·   Avg edge: +4.1 pts                  │
└───────────────────────────────────────────────────────────────────┘
```

- Chart (Recharts line chart): model probability vs normalized market price over time for the chosen outcome.
- The results table later provides the real resume metrics for Phase 8 (matches analyzed, hit rate).

**Needs from the API:** model estimates over time for a match · price snapshots over time · edge log with final results

---

*All numbers above are placeholders, not real model output.*
