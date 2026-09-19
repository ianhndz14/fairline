import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Opportunities } from '../api'
import { mockApi, renderPage, requestedUrls } from '../test/helpers'
import OpportunitiesPage from './OpportunitiesPage'

const opportunities: Opportunities = {
  lastPriceUpdate: new Date().toISOString(),
  minEdge: 0.03,
  opportunities: [
    {
      eventId: 466,
      homeTeam: 'Fulham',
      awayTeam: 'Manchester United',
      kickoff: '2026-09-20T15:30:00Z',
      outcome: 'HOME',
      model: 0.36,
      market: 0.274,
      edge: 0.086,
    },
  ],
}

const openDashboard = (query = '') => renderPage(`/opportunities${query}`, '/opportunities', <OpportunitiesPage />)

describe('OpportunitiesPage', () => {
  it('lists opportunities with a link to each match history', async () => {
    mockApi({ '/api/opportunities': opportunities })
    openDashboard()

    const link = await screen.findByRole('link', { name: 'Fulham v Manchester United' })
    expect(link).toHaveAttribute('href', '/history/466?outcome=HOME')
    expect(screen.getByText('Fulham win')).toBeInTheDocument()
    expect(screen.getByText('+8.6 pts')).toBeInTheDocument()
    expect(screen.getByText(/Prices updated/)).toBeInTheDocument()
  })

  it('starts the filter at the backend threshold and requests the edited one', async () => {
    const fetchMock = mockApi({ '/api/opportunities': opportunities })
    openDashboard()

    const filter = screen.getByLabelText('Minimum edge (pts)')
    await waitFor(() => expect(filter).toHaveValue(3))

    await userEvent.clear(filter)
    await userEvent.type(filter, '10')

    expect(filter).toHaveValue(10)
    await waitFor(() => expect(requestedUrls(fetchMock)).toContain('/api/opportunities?minEdge=0.1'))
  })

  it('says so when nothing clears the threshold', async () => {
    mockApi({ '/api/opportunities': { ...opportunities, opportunities: [] } })
    openDashboard()

    expect(await screen.findByText('No upcoming outcome clears 3.0 pts right now.')).toBeInTheDocument()
  })
})
