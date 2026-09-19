import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Estimate } from '../api'
import { mockApi, renderPage, requestedUrls } from '../test/helpers'
import Calculator from './Calculator'

const teams = ['Fulham', 'Manchester United'].map((team) => ({ team }))

const estimate: Estimate = {
  homeTeam: 'Fulham',
  awayTeam: 'Manchester United',
  homeLambda: 1.51,
  awayLambda: 1.61,
  probabilities: { homeWin: 0.36, draw: 0.237, awayWin: 0.403 },
  scoreGrid: Array.from({ length: 11 }, () => Array<number>(11).fill(1 / 121)),
  market: { HOME: 0.274, DRAW: 0.254, AWAY: 0.472 },
}

const openCalculator = (query: string) => renderPage(`/?${query}`, '/', <Calculator />)

describe('Calculator', () => {
  it('shows model, market and edge for each outcome', async () => {
    mockApi({ '/api/teams': teams, '/api/estimate': estimate })
    openCalculator('home=Fulham&away=Manchester+United')

    expect(await screen.findByText('36.0%')).toBeInTheDocument()
    expect(screen.getByText('27.4%')).toBeInTheDocument()
    expect(screen.getByText('+8.6 pts')).toBeInTheDocument()
    expect(screen.getByText('−6.9 pts')).toBeInTheDocument()
  })

  it('fills expected goals from the model and sends edited values to the API', async () => {
    const fetchMock = mockApi({ '/api/teams': teams, '/api/estimate': estimate })
    openCalculator('home=Fulham&away=Manchester+United')

    const homeLambda = screen.getByLabelText('Home expected goals (λ)')
    await waitFor(() => expect(homeLambda).toHaveValue(1.51))

    await userEvent.clear(homeLambda)
    await userEvent.type(homeLambda, '1')

    await waitFor(() => expect(requestedUrls(fetchMock)).toContainEqual(expect.stringContaining('homeLambda=1')))
    expect(screen.getByRole('button', { name: 'Reset to model values' })).toBeEnabled()
  })

  it('explains when Kalshi has no market for the matchup', async () => {
    mockApi({ '/api/teams': teams, '/api/estimate': { ...estimate, market: null } })
    openCalculator('home=Fulham&away=Manchester+United')

    expect(await screen.findByText(/Kalshi has no open market/)).toBeInTheDocument()
    expect(screen.queryByText(/pts/)).not.toBeInTheDocument()
  })

  it('asks for two different teams instead of calling the API', async () => {
    const fetchMock = mockApi({ '/api/teams': teams })
    openCalculator('home=Fulham&away=Fulham')

    expect(await screen.findByText('Pick two different teams.')).toBeInTheDocument()
    expect(requestedUrls(fetchMock).some((url) => url.startsWith('/api/estimate'))).toBe(false)
  })
})
