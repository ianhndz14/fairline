import { Link, Route, Routes } from 'react-router'

export default function App() {
  return (
    <>
      <header className="site-header">
        <Link to="/" className="brand">Fairline</Link>
      </header>
      <main>
        <Routes>
          <Route path="*" element={<p className="muted">Page not found.</p>} />
        </Routes>
      </main>
    </>
  )
}
