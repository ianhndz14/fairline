import { useState, type CSSProperties } from 'react'
import { useSearchParams } from 'react-router'
import { percent, points, useApi, type Estimate, type Outcome, type Probabilities, type TeamStrength } from '../api'

const OUTCOMES: { key: Outcome; model: keyof Probabilities }[] = [
  { key: 'HOME', model: 'homeWin' },
  { key: 'DRAW', model: 'draw' },
  { key: 'AWAY', model: 'awayWin' },
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
  const result = sameTeam ? undefined : estimate.data
  const overridden = lambdas.home !== null || lambdas.away !== null

  function pickTeam(side: 'home' | 'away', team: string) {
    setParams({ home, away, [side]: team }, { replace: true })
    setLambdas({ home: null, away: null })
  }

  if (teams.error) return <p className="error">Couldn't load teams: {teams.error}</p>

  const outcomeName = (key: Outcome) => (key === 'HOME' ? `Home · ${home}` : key === 'AWAY' ? `Away · ${away}` : 'Draw')

  return (
    <>
      <h1 className="sr-only">Match calculator</h1>
      <div className="split">
        <section className="panel">
          <header className="panel-header">
            <h2 className="panel-title">Match setup</h2>
          </header>
          <div className="panel-body">
            <label className="field">
              <span className="micro">Home</span>
              <select value={home} onChange={(e) => pickTeam('home', e.target.value)}>
                {names.map((name) => (
                  <option key={name}>{name}</option>
                ))}
              </select>
            </label>
            <label className="field">
              <span className="micro">Away</span>
              <select value={away} onChange={(e) => pickTeam('away', e.target.value)}>
                {names.map((name) => (
                  <option key={name}>{name}</option>
                ))}
              </select>
            </label>
            <div className="field-row">
              <label className="field">
                <span className="micro">
                  <span className="greek">λ</span> Home
                </span>
                <input
                  type="number"
                  min="0"
                  step="0.05"
                  inputMode="decimal"
                  aria-label="Home expected goals (λ)"
                  value={lambdas.home ?? result?.homeLambda.toFixed(2) ?? ''}
                  onChange={(e) => setLambdas({ ...lambdas, home: e.target.value })}
                />
              </label>
              <label className="field">
                <span className="micro">
                  <span className="greek">λ</span> Away
                </span>
                <input
                  type="number"
                  min="0"
                  step="0.05"
                  inputMode="decimal"
                  aria-label="Away expected goals (λ)"
                  value={lambdas.away ?? result?.awayLambda.toFixed(2) ?? ''}
                  onChange={(e) => setLambdas({ ...lambdas, away: e.target.value })}
                />
              </label>
            </div>
            <p className="note">
              <span className="greek">λ</span> (expected goals) auto-fills from the last 365 days of results.{' '}
              <button
                type="button"
                className="text-button"
                aria-label="Reset to model values"
                disabled={!overridden}
                onClick={() => setLambdas({ home: null, away: null })}
              >
                Reset
              </button>
            </p>
            <div>
              <div className="kv">
                <span className="micro">Model</span>
                <span>Poisson, independent</span>
              </div>
              <div className="kv">
                <span className="micro">Grid</span>
                <span>0–10 goals, normalized</span>
              </div>
            </div>
          </div>
        </section>

        <section className="panel">
          <header className="panel-header">
            <h2 className="panel-title">1X2 pricing</h2>
            {result && !result.market && <span className="micro">No Kalshi market</span>}
          </header>
          {sameTeam && <p className="error empty">Pick two different teams.</p>}
          {estimate.error && <p className="error empty">{estimate.error}</p>}
          {!sameTeam && (
            <div className="pricing">
              {OUTCOMES.map(({ key, model }) => {
                const modelP = result?.probabilities[model]
                const marketP = result?.market?.[key]
                const edge = modelP !== undefined && marketP !== undefined ? modelP - marketP : undefined
                return (
                  <div key={key}>
                    <div className="micro">{outcomeName(key)}</div>
                    <div className="hero-num">{modelP === undefined ? '—' : percent(modelP)}</div>
                    <div className="pricing-rows">
                      <div className="kv">
                        <span className="micro">Kalshi</span>
                        <span className="num">{marketP === undefined ? '—' : percent(marketP)}</span>
                      </div>
                      <div className="kv">
                        <span className="micro">Edge</span>
                        {edge === undefined ? (
                          <span className="num muted">—</span>
                        ) : (
                          <span className={`num ${edge >= 0 ? 'up' : 'down'}`}>
                            {points(edge)} <span aria-hidden="true">{edge >= 0 ? '▲' : '▼'}</span>
                          </span>
                        )}
                      </div>
                      <div className="bars" aria-hidden="true">
                        <div className="bar-row">
                          <span className="micro">Mod</span>
                          <div className="bar">
                            <span className="bar-model" style={{ width: percent(modelP ?? 0) }} />
                          </div>
                        </div>
                        <div className="bar-row">
                          <span className="micro">Mkt</span>
                          <div className="bar">
                            <span className="bar-market" style={{ width: percent(marketP ?? 0) }} />
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </section>
      </div>

      {result && !result.market && (
        <p className="note">Kalshi has no open market for this matchup, so only the model is shown.</p>
      )}

      {result && (
        <section className="panel">
          <header className="panel-header">
            <h2 className="panel-title">Scoreline matrix</h2>
            <span className="micro">
              Rows: {result.homeTeam} goals · Cols: {result.awayTeam} goals
            </span>
          </header>
          <div className="matrix">
            <ScoreHeatmap grid={result.scoreGrid} />
            <MostLikely grid={result.scoreGrid} />
          </div>
        </section>
      )}
    </>
  )
}

function ScoreHeatmap({ grid }: { grid: number[][] }) {
  const range = [...Array(GRID_SIZE).keys()]
  const max = Math.max(...range.flatMap((h) => range.map((a) => grid[h][a])))
  return (
    <div className="matrix-grid">
      <table className="heatmap">
        <thead>
          <tr>
            <th />
            {range.map((a) => (
              <th key={a} scope="col" className="micro">
                {a}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {range.map((h) => (
            <tr key={h}>
              <th scope="row" className="micro">
                {h}
              </th>
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

/** The five most likely exact scores across the whole 0-10 grid. */
function MostLikely({ grid }: { grid: number[][] }) {
  const top = grid
    .flatMap((row, h) => row.map((p, a) => ({ h, a, p })))
    .sort((x, y) => y.p - x.p)
    .slice(0, 5)
  return (
    <div className="likely">
      <div className="micro">Most likely</div>
      {top.map(({ h, a, p }) => (
        <div className="kv" key={`${h}-${a}`}>
          <span className="num">
            {h}–{a}
          </span>
          <span className="num">{percent(p)}</span>
        </div>
      ))}
    </div>
  )
}
