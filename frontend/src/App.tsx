import { Link, NavLink, Route, Routes } from 'react-router'
import Calculator from './pages/Calculator'
import OpportunitiesPage from './pages/OpportunitiesPage'

export default function App() {
  return (
    <>
      <header className="site-header">
        <Link to="/" className="brand">Fairline</Link>
        <nav>
          <NavLink to="/" end>Calculator</NavLink>
          <NavLink to="/opportunities">Opportunities</NavLink>
        </nav>
      </header>
      <main>
        <Routes>
          <Route path="/" element={<Calculator />} />
          <Route path="/opportunities" element={<OpportunitiesPage />} />
          <Route path="*" element={<p className="muted">Page not found.</p>} />
        </Routes>
      </main>
    </>
  )
}
