import { useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import {
  formatKickoff,
  outcomeLabel,
  percent,
  points,
  useApi,
  type EventSummary,
  type History,
  type Outcome,
  type TrackRecord,
} from '../api'

const OUTCOMES: Outcome[] = ['HOME', 'DRAW', 'AWAY']

/** Below this many settled edges, a hit rate says more about luck than about the model. */
const MIN_SAMPLE = 30

const chartTime = new Intl.DateTimeFormat(undefined, { weekday: 'short', hour: '2-digit', minute: '2-digit' })

const matchName = (e: EventSummary) => `${e.homeTeam} v ${e.awayTeam}`

export default function HistoryPage() {
  const events = useApi<EventSummary[]>('/api/events')
  const { eventId } = useParams()
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const outcome = OUTCOMES.find((o) => o === params.get('outcome')) ?? 'HOME'

  // Without an id in the URL, show the next match to kick off (or the latest one if all have started).
  const [openedAt] = useState(Date.now)
  const list = events.data ?? []
  const fallback = list.find((e) => Date.parse(e.kickoff) > openedAt) ?? list.at(-1)
  const selectedId = eventId ?? fallback?.id.toString()

  const history = useApi<History>(selectedId ? `/api/events/${selectedId}/history` : null)
  const h = history.data?.event.id.toString() === selectedId ? history.data : undefined

  return (
    <>
      <h1>History</h1>
      <p className="muted">
        How the model and Kalshi's normalized price moved before kickoff, and how flagged outcomes turned out.
      </p>

      {events.error && <p className="error">{events.error}</p>}
      {events.data && list.length === 0 && (
        <p className="muted">No priced matches yet. Prices are fetched every 10 minutes.</p>
      )}

      {list.length > 0 && (
        <div className="toolbar">
          <label className="wide">
            Match
            <select value={selectedId} onChange={(e) => navigate(`/history/${e.target.value}?outcome=${outcome}`)}>
              {list.map((e) => (
                <option key={e.id} value={e.id}>
                  {matchName(e)} — {formatKickoff(e.kickoff)}
                </option>
              ))}
            </select>
          </label>
          <div className="segmented" role="group" aria-label="Outcome">
            {OUTCOMES.map((o) => (
              <button
                key={o}
                type="button"
                aria-pressed={o === outcome}
                onClick={() => setParams({ outcome: o }, { replace: true })}
              >
                {h ? outcomeLabel(o, h.event.homeTeam, h.event.awayTeam) : o}
              </button>
            ))}
          </div>
        </div>
      )}

      {history.error && <p className="error">{history.error}</p>}
      {h && <PriceChart history={h} outcome={outcome} />}

      <TrackRecordSection />
    </>
  )
}

function PriceChart({ history, outcome }: { history: History; outcome: Outcome }) {
  if (history.points.length < 2) {
    return (
      <p className="muted">
        Only {history.points.length} price snapshot so far. The chart fills in as prices are collected every 10 minutes.
      </p>
    )
  }
  const data = history.points.map((p) => ({
    time: Date.parse(p.time),
    model: p.model[outcome],
    market: p.market[outcome],
  }))
  return (
    <div
      className="card chart"
      aria-label={`Model vs market for ${outcomeLabel(outcome, history.event.homeTeam, history.event.awayTeam)}`}
    >
      <ResponsiveContainer width="100%" height={300}>
        <LineChart data={data} margin={{ top: 8, right: 16, bottom: 0, left: 0 }}>
          <CartesianGrid stroke="var(--border)" vertical={false} />
          <XAxis
            dataKey="time"
            type="number"
            scale="time"
            domain={['dataMin', 'dataMax']}
            tickFormatter={(t: number) => chartTime.format(t)}
            stroke="var(--muted)"
            fontSize={12}
            minTickGap={40}
          />
          <YAxis
            tickFormatter={(p: number) => `${Math.round(p * 100)}%`}
            stroke="var(--muted)"
            fontSize={12}
            domain={[(min: number) => Math.max(0, min - 0.03), (max: number) => Math.min(1, max + 0.03)]}
            width={44}
          />
          <Tooltip
            labelFormatter={(t) => chartTime.format(Number(t))}
            formatter={(value) => percent(Number(value))}
            contentStyle={{ background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 6 }}
          />
          <Legend />
          <Line name="Model" dataKey="model" type="stepAfter" stroke="var(--accent)" strokeWidth={2.5} dot={false} />
          <Line
            name="Kalshi (normalized)"
            dataKey="market"
            type="linear"
            stroke="var(--market)"
            strokeWidth={2}
            strokeDasharray="5 4"
            dot={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}

function TrackRecordSection() {
  const { data, error } = useApi<TrackRecord>('/api/track-record')
  if (error) return <p className="error">{error}</p>
  if (!data) return null

  return (
    <>
      <h2>Track record</h2>
      <p className="muted small">Each flagged outcome is judged once, by its last edge before kickoff.</p>
      {data.hitRate !== null && data.averageMarket !== null && data.averageModel !== null ? (
        <p>
          Hit rate <strong>{percent(data.hitRate)}</strong> ({data.hits} of {data.settled}), against{' '}
          {percent(data.averageMarket)} expected by the market and {percent(data.averageModel)} by the model.
          {data.settled < MIN_SAMPLE && (
            <span className="muted"> Too few settled matches to tell skill from luck yet.</span>
          )}
        </p>
      ) : (
        <p className="muted">No flagged match has a result yet. Results are imported every 6 hours.</p>
      )}

      {data.edges.length > 0 && (
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th scope="col">Kickoff</th>
                <th scope="col">Match</th>
                <th scope="col">Flagged</th>
                <th scope="col" className="num">
                  Edge
                </th>
                <th scope="col" className="num">
                  Result
                </th>
                <th scope="col" className="num">
                  Hit
                </th>
              </tr>
            </thead>
            <tbody>
              {data.edges.map((t) => (
                <tr key={`${t.event.id}-${t.outcome}`}>
                  <td className="nowrap">{formatKickoff(t.event.kickoff)}</td>
                  <td>
                    <Link to={`/history/${t.event.id}?outcome=${t.outcome}`}>{matchName(t.event)}</Link>
                  </td>
                  <td>{outcomeLabel(t.outcome, t.event.homeTeam, t.event.awayTeam)}</td>
                  <td className="num">{points(t.edge)}</td>
                  <td className="num">
                    {t.event.homeGoals === null ? '–' : `${t.event.homeGoals}–${t.event.awayGoals}`}
                  </td>
                  <td className="num">
                    {t.hit === null ? (
                      <span className="muted">pending</span>
                    ) : t.hit ? (
                      <span className="positive" aria-label="hit">
                        ✓
                      </span>
                    ) : (
                      <span className="negative" aria-label="miss">
                        ✗
                      </span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}
