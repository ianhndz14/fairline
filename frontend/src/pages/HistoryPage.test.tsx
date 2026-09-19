import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { EventSummary, History, TrackRecord, TrackedEdge } from '../api'
import { mockApi, renderPage } from '../test/helpers'
import HistoryPage from './HistoryPage'

const match = (id: number, homeGoals: number | null, awayGoals: number | null): EventSummary => ({
  id,
  homeTeam: `Home ${id}`,
  awayTeam: `Away ${id}`,
  kickoff: '2026-09-19T14:00:00Z',
  homeGoals,
  awayGoals,
})

const edge = (event: EventSummary, hit: boolean | null): TrackedEdge => ({
  event,
  outcome: 'HOME',
  detectedAt: '2026-09-19T13:50:00Z',
  model: 0.4,
  market: 0.3,
  edge: 0.1,
  hit,
})

const trackRecord: TrackRecord = {
  settled: 2,
  hits: 1,
  hitRate: 0.5,
  averageModel: 0.4,
  averageMarket: 0.3,
  edges: [edge(match(1, 2, 0), true), edge(match(2, 0, 1), false), edge(match(3, null, null), null)],
}

const history: History = {
  event: match(1, 2, 0),
  points: [
    {
      time: '2026-09-19T12:00:00Z',
      model: { HOME: 0.4, DRAW: 0.3, AWAY: 0.3 },
      market: { HOME: 0.3, DRAW: 0.3, AWAY: 0.4 },
    },
  ],
}

describe('HistoryPage', () => {
  it('compares the hit rate with market and model expectations, flagging small samples', async () => {
    mockApi({ '/api/events': [match(1, 2, 0)], '/api/events/1/history': history, '/api/track-record': trackRecord })
    renderPage('/history/1', '/history/:eventId?', <HistoryPage />)

    expect(await screen.findByText('50.0%')).toBeInTheDocument()
    expect(
      screen.getByText(/1 of 2\), against 30.0% expected by the market and 40.0% by the model/),
    ).toBeInTheDocument()
    expect(screen.getByText(/Too few settled matches/)).toBeInTheDocument()
    expect(screen.getByLabelText('hit')).toBeInTheDocument()
    expect(screen.getByLabelText('miss')).toBeInTheDocument()
    expect(screen.getByText('pending')).toBeInTheDocument()
  })

  it('names outcome buttons after the teams and explains a chart with too little data', async () => {
    mockApi({ '/api/events': [match(1, 2, 0)], '/api/events/1/history': history, '/api/track-record': trackRecord })
    renderPage('/history/1', '/history/:eventId?', <HistoryPage />)

    expect(await screen.findByRole('button', { name: 'Home 1 win' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByText(/Only 1 price snapshot so far/)).toBeInTheDocument()
  })
})
