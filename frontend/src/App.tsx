import { lazy, Suspense } from 'react'
import { Link, NavLink, Route, Routes } from 'react-router'
import Calculator from './pages/Calculator'
import OpportunitiesPage from './pages/OpportunitiesPage'

// Loaded on demand so the charting library only downloads when someone opens History.
const HistoryPage = lazy(() => import('./pages/HistoryPage'))

export default function App() {
  return (
    <>
      <header className="site-header">
        <Link to="/" className="brand">Fairline</Link>
        <nav>
          <NavLink to="/" end>Calculator</NavLink>
          <NavLink to="/opportunities">Opportunities</NavLink>
          <NavLink to="/history">History</NavLink>
        </nav>
      </header>
      <main>
        <Suspense fallback={<p className="muted">Loading…</p>}>
          <Routes>
            <Route path="/" element={<Calculator />} />
            <Route path="/opportunities" element={<OpportunitiesPage />} />
            <Route path="/history/:eventId?" element={<HistoryPage />} />
            <Route path="*" element={<p className="muted">Page not found.</p>} />
          </Routes>
        </Suspense>
      </main>
    </>
  )
}
