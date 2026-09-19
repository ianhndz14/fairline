# Fairline — Frontend

React 19 + TypeScript web app for Fairline, built with Vite.

## Run it

Requires Node.js 20.19+ (tested on 24) and the [backend](../backend/README.md) running on port 8080.

```bash
npm install
npm run dev      # http://localhost:5173
```

In development, Vite forwards every `/api/...` request to `http://localhost:8080`, so the browser only talks to one origin.

## Scripts

| Command | What it does |
| --- | --- |
| `npm run dev` | Dev server with instant reload |
| `npm run build` | Type-check with `tsc`, then build static files into `dist/` |
| `npm run lint` | Lint with oxlint |
| `npm run preview` | Serve the production build locally |
