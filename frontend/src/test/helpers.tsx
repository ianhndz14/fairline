import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { vi } from 'vitest'

/**
 * Replaces fetch with canned JSON responses keyed by path (query string ignored), so components
 * can be tested without the backend. Returns the mock to inspect which URLs were requested.
 */
export function mockApi(responses: Record<string, unknown>) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
    const path = String(input).split('?')[0]
    if (!(path in responses)) return new Response(JSON.stringify({ detail: `No mock for ${path}` }), { status: 404 })
    return new Response(JSON.stringify(responses[path]), { headers: { 'Content-Type': 'application/json' } })
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

export const requestedUrls = (fetchMock: ReturnType<typeof mockApi>) => fetchMock.mock.calls.map(([url]) => String(url))

/** Renders `element` as the page for `route`, starting at `url`. */
export function renderPage(url: string, route: string, element: ReactElement) {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <Routes>
        <Route path={route} element={element} />
      </Routes>
    </MemoryRouter>,
  )
}
