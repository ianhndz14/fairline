import { lazy, Suspense, useState } from 'react'
import { Link, NavLink, Route, Routes } from 'react-router'
import { timeAgo, useApi, type Opportunities } from './api'
import Calculator from './pages/Calculator'
import OpportunitiesPage from './pages/OpportunitiesPage'

// Loaded on demand so the charting library only downloads when someone opens History.
const HistoryPage = lazy(() => import('./pages/HistoryPage'))

/** Prices older than this mean the ingestion job (every 10 minutes) has stopped. */
const STALE_AFTER_MS = 20 * 60_000

export default function App() {
  return (
    <>
      <header className="topbar">
        <div className="topbar-left">
          <Link to="/" className="brand">
            <span className="brand-mark" aria-hidden="true" />
            FAIRLINE
          </Link>
          <nav className="tabs" aria-label="Main">
            <NavLink to="/" end>
              Calculator
            </NavLink>
            <NavLink to="/opportunities">Opportunities</NavLink>
            <NavLink to="/history">History</NavLink>
          </nav>
        </div>
        <FeedStatus />
      </header>
      <main>
        <Suspense fallback={<p className="empty">Loading…</p>}>
          <Routes>
            <Route path="/" element={<Calculator />} />
            <Route path="/opportunities" element={<OpportunitiesPage />} />
            <Route path="/history/:eventId?" element={<HistoryPage />} />
            <Route path="*" element={<p className="empty">Page not found.</p>} />
          </Routes>
        </Suspense>
      </main>
    </>
  )
}

/** Market, feed health and last price update, from the backend's latest Kalshi snapshot. */
function FeedStatus() {
  const { data } = useApi<Opportunities>('/api/opportunities')
  const [loadedAt] = useState(Date.now)
  const updated = data?.lastPriceUpdate
  const live = updated !== undefined && updated !== null && loadedAt - Date.parse(updated) < STALE_AFTER_MS
  return (
    <div className="status micro">
      <span>EPL · 1X2</span>
      <span className="status-feed">
        <span className={`dot ${data ? (live ? 'live' : 'stale') : ''}`} aria-hidden="true" />
        {data && !live ? 'Kalshi feed stale' : 'Kalshi feed'}
      </span>
      {updated && <span>Upd {timeAgo(updated)}</span>}
    </div>
  )
}
