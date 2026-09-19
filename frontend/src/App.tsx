import { Link, NavLink, Route, Routes } from 'react-router'
import Calculator from './pages/Calculator'

export default function App() {
  return (
    <>
      <header className="site-header">
        <Link to="/" className="brand">Fairline</Link>
        <nav>
          <NavLink to="/" end>Calculator</NavLink>
        </nav>
      </header>
      <main>
        <Routes>
          <Route path="/" element={<Calculator />} />
          <Route path="*" element={<p className="muted">Page not found.</p>} />
        </Routes>
      </main>
    </>
  )
}
