# Fairline — Frontend

React 19 + TypeScript web app for Fairline, built with Vite.

## Design

A "trading terminal" look: IBM Plex Sans for text and IBM Plex Mono for every number, hairline-bordered panels,
one amber signal colour for the model, green/red only for edges. Light and dark palettes follow the system setting
and are defined as CSS custom properties in `src/index.css`.

## Screens

- **Calculator** (`/`): pick two teams to see the Poisson model's home/draw/away probabilities, a scoreline heatmap and Kalshi's normalized price. Expected goals can be edited to explore what-ifs.
- **Opportunities** (`/opportunities`): upcoming outcomes the model rates higher than the market, sorted by edge, with an adjustable minimum edge.
- **History** (`/history/:eventId`): model vs market over time for one match (Recharts), and a track record of past flagged outcomes with hit rate. Lazy-loaded so the chart library isn't in the main bundle.

## Run it

Requires Node.js 20.19+ (tested on 24) and the [backend](../backend/README.md) running on port 8080.

```bash
npm install
npm run dev      # http://localhost:5173
```

In development, Vite forwards every `/api/...` request to `http://localhost:8080`, so the browser only talks to one origin.

## Scripts

| Command           | What it does                                                |
| ----------------- | ----------------------------------------------------------- |
| `npm run dev`     | Dev server with instant reload                              |
| `npm run build`   | Type-check with `tsc`, then build static files into `dist/` |
| `npm run lint`    | Lint with oxlint                                            |
| `npm run format`  | Format with Prettier (`format:check` only reports)          |
| `npm test`        | Component tests with Vitest + React Testing Library         |
| `npm run preview` | Serve the production build locally                          |
