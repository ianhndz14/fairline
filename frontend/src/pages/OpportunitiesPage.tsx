import { Link, useSearchParams } from 'react-router'
import { formatKickoff, outcomeLabel, percent, points, timeAgo, useApi, type Opportunities } from '../api'

export default function OpportunitiesPage() {
  // Minimum edge in percentage points, kept in the URL. Absent = show the backend's configured threshold;
  // empty = the user cleared the box to type a new value (the default still applies meanwhile).
  const [params, setParams] = useSearchParams()
  const minEdge = params.get('minEdge')
  const validMinEdge = minEdge !== null && minEdge.trim() !== '' && Number.isFinite(Number(minEdge))
  const { data, error, loading } = useApi<Opportunities>(
    validMinEdge ? `/api/opportunities?minEdge=${Number(minEdge) / 100}` : '/api/opportunities',
  )

  return (
    <>
      <h1>Opportunities</h1>
      <p className="muted">
        Upcoming outcomes the model rates higher than Kalshi's normalized price, biggest edge first.
      </p>

      <div className="toolbar">
        <label>
          Minimum edge (pts)
          <input type="number" step="0.5" inputMode="decimal"
            value={minEdge ?? (data ? (data.minEdge * 100).toString() : '')}
            onChange={(e) => setParams({ minEdge: e.target.value }, { replace: true })} />
        </label>
        {data && (
          <span className="muted small">
            {data.lastPriceUpdate ? `Prices updated ${timeAgo(data.lastPriceUpdate)}` : 'No prices fetched yet'}
            {' · '}{data.opportunities.length} {data.opportunities.length === 1 ? 'outcome' : 'outcomes'}
          </span>
        )}
      </div>

      {error && <p className="error">{error}</p>}
      {loading && !data && <p className="muted">Loading…</p>}

      {data && data.opportunities.length === 0 && (
        <p className="muted">No upcoming outcome clears {(data.minEdge * 100).toFixed(1)} pts right now.</p>
      )}

      {data && data.opportunities.length > 0 && (
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th scope="col">Kickoff</th>
                <th scope="col">Match</th>
                <th scope="col">Outcome</th>
                <th scope="col" className="num">Model</th>
                <th scope="col" className="num">Market</th>
                <th scope="col" className="num">Edge</th>
              </tr>
            </thead>
            <tbody>
              {data.opportunities.map((o) => (
                <tr key={`${o.eventId}-${o.outcome}`}>
                  <td className="nowrap">{formatKickoff(o.kickoff)}</td>
                  <td><Link to={`/history/${o.eventId}?outcome=${o.outcome}`}>{o.homeTeam} v {o.awayTeam}</Link></td>
                  <td>{outcomeLabel(o.outcome, o.homeTeam, o.awayTeam)}</td>
                  <td className="num">{percent(o.model)}</td>
                  <td className="num">{percent(o.market)}</td>
                  <td className="num positive">{points(o.edge)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}
