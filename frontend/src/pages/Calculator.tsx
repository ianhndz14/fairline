import { useState, type CSSProperties } from 'react'
import { useSearchParams } from 'react-router'
import { percent, points, useApi, type Estimate, type Outcome, type Probabilities, type TeamStrength } from '../api'

const OUTCOMES: { key: Outcome; label: string; model: keyof Probabilities }[] = [
  { key: 'HOME', label: 'Home win', model: 'homeWin' },
  { key: 'DRAW', label: 'Draw', model: 'draw' },
  { key: 'AWAY', label: 'Away win', model: 'awayWin' },
]

/** Scorelines shown in the heatmap: 0-5 goals per side covers ~99% of the probability. */
const GRID_SIZE = 6

const isValidLambda = (value: string | null): value is string =>
  value !== null && value.trim() !== '' && Number.isFinite(Number(value)) && Number(value) >= 0

export default function Calculator() {
  const teams = useApi<TeamStrength[]>('/api/teams')
  const names = teams.data?.map((t) => t.team) ?? []

  // Teams live in the URL so a matchup can be bookmarked or linked to.
  const [params, setParams] = useSearchParams()
  const home = params.get('home') ?? names[0] ?? ''
  const away = params.get('away') ?? names[1] ?? ''

  // null = use the model's expected goals; a string = the user's override while they type.
  const [lambdas, setLambdas] = useState<{ home: string | null; away: string | null }>({ home: null, away: null })

  const query = new URLSearchParams({ home, away })
  if (isValidLambda(lambdas.home)) query.set('homeLambda', lambdas.home)
  if (isValidLambda(lambdas.away)) query.set('awayLambda', lambdas.away)
  const sameTeam = home !== '' && home === away
  const estimate = useApi<Estimate>(home && away && !sameTeam ? `/api/estimate?${query}` : null)
  const result = estimate.data

  function pickTeam(side: 'home' | 'away', team: string) {
    setParams({ home, away, [side]: team }, { replace: true })
    setLambdas({ home: null, away: null })
  }

  if (teams.error) return <p className="error">Couldn't load teams: {teams.error}</p>

  return (
    <>
      <h1>Match calculator</h1>
      <p className="muted">
        Pick two teams to see the Poisson model's probabilities, next to Kalshi's price when the match is listed.
      </p>

      <div className="controls">
        <label>
          Home team
          <select value={home} onChange={(e) => pickTeam('home', e.target.value)}>
            {names.map((name) => (
              <option key={name}>{name}</option>
            ))}
          </select>
        </label>
        <label>
          Away team
          <select value={away} onChange={(e) => pickTeam('away', e.target.value)}>
            {names.map((name) => (
              <option key={name}>{name}</option>
            ))}
          </select>
        </label>
        <label>
          Home expected goals (λ)
          <input
            type="number"
            min="0"
            step="0.05"
            inputMode="decimal"
            value={lambdas.home ?? result?.homeLambda.toFixed(2) ?? ''}
            onChange={(e) => setLambdas({ ...lambdas, home: e.target.value })}
          />
        </label>
        <label>
          Away expected goals (λ)
          <input
            type="number"
            min="0"
            step="0.05"
            inputMode="decimal"
            value={lambdas.away ?? result?.awayLambda.toFixed(2) ?? ''}
            onChange={(e) => setLambdas({ ...lambdas, away: e.target.value })}
          />
        </label>
      </div>
      <p className="muted small">
        λ is filled in from each team's last year of results.{' '}
        {(lambdas.home !== null || lambdas.away !== null) && (
          <button type="button" className="link" onClick={() => setLambdas({ home: null, away: null })}>
            Reset to model values
          </button>
        )}
      </p>

      {sameTeam && <p className="error">Pick two different teams.</p>}
      {estimate.error && <p className="error">{estimate.error}</p>}

      {result && !sameTeam && (
        <>
          <div className="cards">
            {OUTCOMES.map(({ key, label, model }) => {
              const modelP = result.probabilities[model]
              const marketP = result.market?.[key]
              const edge = marketP === undefined ? undefined : modelP - marketP
              return (
                <div className="card outcome" key={key}>
                  <div className="card-label">{label}</div>
                  <div className="big">{percent(modelP)}</div>
                  <div className="muted small">model</div>
                  {marketP !== undefined && edge !== undefined && (
                    <>
                      <div className="market-line">
                        {percent(marketP)} <span className="muted small">Kalshi (normalized)</span>
                      </div>
                      <div className={edge >= 0 ? 'positive' : 'negative'}>{points(edge)} edge</div>
                    </>
                  )}
                </div>
              )
            })}
          </div>
          {!result.market && (
            <p className="muted small">Kalshi has no open market for this matchup, so only the model is shown.</p>
          )}

          <h2>Scoreline probabilities</h2>
          <ScoreHeatmap grid={result.scoreGrid} homeTeam={result.homeTeam} awayTeam={result.awayTeam} />
        </>
      )}
    </>
  )
}

function ScoreHeatmap({ grid, homeTeam, awayTeam }: { grid: number[][]; homeTeam: string; awayTeam: string }) {
  const range = [...Array(GRID_SIZE).keys()]
  const max = Math.max(...range.flatMap((h) => range.map((a) => grid[h][a])))
  return (
    <div className="heatmap-wrap">
      <table className="heatmap">
        <caption className="muted small">
          Rows: {homeTeam} goals · Columns: {awayTeam} goals
        </caption>
        <thead>
          <tr>
            <th />
            {range.map((a) => (
              <th key={a} scope="col">
                {a}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {range.map((h) => (
            <tr key={h}>
              <th scope="row">{h}</th>
              {range.map((a) => (
                <td
                  key={a}
                  title={`${h}–${a}: ${percent(grid[h][a])}`}
                  style={{ '--strength': grid[h][a] / max } as CSSProperties}
                >
                  {percent(grid[h][a])}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
