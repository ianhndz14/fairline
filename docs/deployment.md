# Deployment

Fairline deploys as three free pieces:

| Piece | Host | Why |
| --- | --- | --- |
| PostgreSQL | [Neon](https://neon.tech) | Free tier with no expiry date |
| API (Spring Boot, Docker) | [Render](https://render.com) | Free web service, builds the `backend/Dockerfile` |
| Frontend (static build) | [Vercel](https://vercel.com) | Free static hosting, builds `frontend/` |

Render's own free PostgreSQL expires 30 days after creation, which is why the database lives on Neon instead.

## 1. Database (Neon)

1. Create a project at [neon.tech](https://neon.tech) and copy the connection details (host, database, user, password).
2. Nothing else is needed: Flyway creates the tables on the API's first start.

## 2. API (Render)

1. New → **Blueprint**, pick this repository. Render reads [`render.yaml`](../render.yaml) and creates the `fairline-api` service.
2. Set the environment variables it asks for:

   | Key | Value |
   | --- | --- |
   | `DB_HOST` | Neon host, e.g. `ep-xxx.eu-central-1.aws.neon.tech` |
   | `DB_PORT` | `5432` |
   | `DB_NAME` | Neon database name |
   | `DB_USERNAME` / `DB_PASSWORD` | Neon credentials |
   | `CORS_ORIGINS` | the Vercel URL from step 3, e.g. `https://fairline.vercel.app` |

3. Deploy. The first build takes a few minutes; `/actuator/health` should then return `{"status":"UP"}`, and
   `/api/teams` returns the imported teams within a minute.

## 3. Frontend (Vercel)

1. New Project → import this repository → set **Root Directory** to `frontend`. Vercel detects Vite and reads
   [`vercel.json`](../frontend/vercel.json) (its rewrite makes deep links like `/history/42` work).
2. Add environment variable `VITE_API_BASE_URL` = the Render URL, e.g. `https://fairline-api.onrender.com`.
3. Deploy, then put that Vercel URL into Render's `CORS_ORIGINS` and redeploy the API.

## What the free tiers mean

- **The API sleeps after 15 minutes without traffic** and takes ~1 minute to wake. The first page load after a quiet
  period is slow, and price collection pauses while it sleeps, so history charts have gaps.
- **750 free instance hours per month** across the workspace.
- Neon's free tier also idles, but wakes in well under a second.

Both are fine for a portfolio demo; a paid instance removes the sleeping.

## Environment variables in one place

| Variable | Where | Default |
| --- | --- | --- |
| `PORT` | API | `8080` (Render sets it) |
| `DB_URL` or `DB_HOST`/`DB_PORT`/`DB_NAME` | API | `jdbc:postgresql://localhost:5432/fairline` |
| `DB_USERNAME`, `DB_PASSWORD` | API | `fairline`, empty |
| `CORS_ORIGINS` | API | empty (no cross-origin access) |
| `FAIRLINE_EDGE_THRESHOLD` (`fairline.edge.threshold`) | API | `0.03` |
| `VITE_API_BASE_URL` | Frontend build | empty (same origin via the dev proxy) |
