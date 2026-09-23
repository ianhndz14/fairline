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
      <h1 className="sr-only">History</h1>
      <section className="panel">
        <header className="panel-header">
          <h2 className="panel-title">Price history · model vs Kalshi</h2>
          {h && <span className="micro">{h.points.length} snapshots</span>}
        </header>
        {events.error && <p className="error empty">{events.error}</p>}
        {events.data && list.length === 0 && (
          <p className="empty">No priced matches yet. Prices are fetched every 10 minutes.</p>
        )}
        {list.length > 0 && (
          <div className="panel-body">
            <div className="toolbar">
              <label className="field wide">
                <span className="micro">Match</span>
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
          </div>
        )}
        {history.error && <p className="error empty">{history.error}</p>}
        {h && <PriceChart history={h} outcome={outcome} />}
      </section>

      <TrackRecordSection />
    </>
  )
}

function PriceChart({ history, outcome }: { history: History; outcome: Outcome }) {
  if (history.points.length < 2) {
    return (
      <p className="empty">
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
      className="chart"
      aria-label={`Model vs market for ${outcomeLabel(outcome, history.event.homeTeam, history.event.awayTeam)}`}
    >
      <ResponsiveContainer width="100%" height={320}>
        <LineChart data={data} margin={{ top: 8, right: 16, bottom: 0, left: 0 }}>
          <CartesianGrid stroke="var(--line)" vertical={false} />
          <XAxis
            dataKey="time"
            type="number"
            scale="time"
            domain={['dataMin', 'dataMax']}
            tickFormatter={(t: number) => chartTime.format(t)}
            stroke="var(--muted)"
            fontSize={11}
            minTickGap={40}
          />
          <YAxis
            tickFormatter={(p: number) => `${Math.round(p * 100)}%`}
            stroke="var(--muted)"
            fontSize={11}
            domain={[(min: number) => Math.max(0, min - 0.03), (max: number) => Math.min(1, max + 0.03)]}
            width={44}
          />
          <Tooltip
            labelFormatter={(t) => chartTime.format(Number(t))}
            formatter={(value) => percent(Number(value))}
            contentStyle={{
              background: 'var(--panel)',
              border: '1px solid var(--line)',
              borderRadius: 2,
              fontFamily: 'var(--mono)',
              fontSize: 12,
            }}
          />
          <Legend wrapperStyle={{ fontFamily: 'var(--mono)', fontSize: 11, textTransform: 'uppercase' }} />
          <Line
            name="Model"
            dataKey="model"
            type="stepAfter"
            stroke="var(--signal)"
            strokeWidth={2}
            dot={false}
            isAnimationActive={false}
          />
          <Line
            name="Kalshi (normalized)"
            dataKey="market"
            type="linear"
            stroke="var(--market)"
            strokeWidth={1.5}
            strokeDasharray="5 4"
            dot={false}
            isAnimationActive={false}
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

  const judged = data.hitRate !== null
  const stat = (value: number | null) => (value === null ? '—' : percent(value))
  return (
    <section className="panel">
      <header className="panel-header">
        <h2 className="panel-title">Track record</h2>
        <span className="micro">Judged by closing edge</span>
      </header>
      <div className="stats">
        <div>
          <span className="micro">Hit rate</span>
          <span className="num">{stat(data.hitRate)}</span>
        </div>
        <div>
          <span className="micro">Hits / settled</span>
          <span className="num">
            {data.hits}/{data.settled}
          </span>
        </div>
        <div>
          <span className="micro">Market expected</span>
          <span className="num">{stat(data.averageMarket)}</span>
        </div>
        <div>
          <span className="micro">Model expected</span>
          <span className="num">{stat(data.averageModel)}</span>
        </div>
      </div>
      <div className="panel-body">
        <p className="note">
          {!judged
            ? 'No flagged match has a result yet. Results are imported every 6 hours.'
            : data.settled < MIN_SAMPLE
              ? 'Too few settled matches to tell skill from luck yet.'
              : 'A hit rate above what the market expected means the flagged edges were real.'}
        </p>
      </div>

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
                  <td className="mono nowrap muted">{formatKickoff(t.event.kickoff)}</td>
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
                      <span className="micro">Pending</span>
                    ) : t.hit ? (
                      <span className="up" aria-label="hit">
                        ✓
                      </span>
                    ) : (
                      <span className="down" aria-label="miss">
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
    </section>
  )
}
