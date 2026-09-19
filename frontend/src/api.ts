import { useEffect, useState } from 'react'

// Types mirror the backend's JSON (see backend/README.md#api).

export type Outcome = 'HOME' | 'DRAW' | 'AWAY'

export interface Probabilities {
  homeWin: number
  draw: number
  awayWin: number
}

export interface TeamStrength {
  team: string
  matches: number
  lastMatch: string
  homeScored: number
  homeConceded: number
  awayScored: number
  awayConceded: number
}

export interface Estimate {
  homeTeam: string
  awayTeam: string
  homeLambda: number
  awayLambda: number
  probabilities: Probabilities
  /** scoreGrid[homeGoals][awayGoals], 0-10 goals each, summing to 1. */
  scoreGrid: number[][]
  /** Normalized Kalshi probabilities, or null if the match isn't listed. */
  market: Record<Outcome, number> | null
}

export interface Opportunity {
  eventId: number
  homeTeam: string
  awayTeam: string
  kickoff: string
  outcome: Outcome
  model: number
  market: number
  edge: number
}

export interface Opportunities {
  /** When prices were last fetched from Kalshi; null before the first fetch. */
  lastPriceUpdate: string | null
  minEdge: number
  opportunities: Opportunity[]
}

export interface EventSummary {
  id: number
  homeTeam: string
  awayTeam: string
  kickoff: string
  homeGoals: number | null
  awayGoals: number | null
}

export interface HistoryPoint {
  time: string
  model: Record<Outcome, number>
  market: Record<Outcome, number>
}

export interface History {
  event: EventSummary
  points: HistoryPoint[]
}

export interface TrackedEdge {
  event: EventSummary
  outcome: Outcome
  detectedAt: string
  model: number
  market: number
  edge: number
  /** null until the result is known. */
  hit: boolean | null
}

export interface TrackRecord {
  settled: number
  hits: number
  hitRate: number | null
  averageModel: number | null
  averageMarket: number | null
  edges: TrackedEdge[]
}

/** GETs a backend endpoint, throwing the server's problem-detail message on failure. */
export async function getJson<T>(path: string, signal?: AbortSignal): Promise<T> {
  const response = await fetch(path, { signal })
  if (!response.ok) {
    const problem = await response.json().catch(() => null)
    throw new Error(problem?.detail ?? `Request failed (${response.status})`)
  }
  return response.json()
}

interface ApiState<T> {
  data?: T
  error?: string
  loading: boolean
}

/**
 * Loads `path` whenever it changes (null = don't load). Keeps showing the previous data while
 * the next request is in flight, and cancels requests that are no longer needed.
 */
export function useApi<T>(path: string | null): ApiState<T> {
  // Remembering which path a result belongs to lets loading be derived instead of stored.
  const [result, setResult] = useState<{ path: string; data?: T; error?: string }>()

  useEffect(() => {
    if (path === null) return
    const controller = new AbortController()
    getJson<T>(path, controller.signal)
      .then((data) => setResult({ path, data }))
      .catch((error: Error) => {
        if (!controller.signal.aborted) setResult({ path, error: error.message })
      })
    return () => controller.abort()
  }, [path])

  return {
    data: result?.data,
    error: result?.path === path ? result.error : undefined,
    loading: path !== null && result?.path !== path,
  }
}

export const percent = (p: number) => `${(p * 100).toFixed(1)}%`

/** An edge in percentage points, e.g. +4.3 pts. */
export const points = (edge: number) => `${edge >= 0 ? '+' : '−'}${Math.abs(edge * 100).toFixed(1)} pts`

export const outcomeLabel = (outcome: Outcome, homeTeam: string, awayTeam: string) =>
  outcome === 'DRAW' ? 'Draw' : `${outcome === 'HOME' ? homeTeam : awayTeam} win`

const kickoffFormat = new Intl.DateTimeFormat(undefined, {
  weekday: 'short', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit',
})

/** Kickoff in the viewer's own timezone, e.g. "Sun 20 Sep, 11:30". */
export const formatKickoff = (iso: string) => kickoffFormat.format(new Date(iso))

const relativeFormat = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' })

/** "5 minutes ago", "2 hours ago". */
export function timeAgo(iso: string) {
  const minutes = Math.round((Date.parse(iso) - Date.now()) / 60_000)
  return Math.abs(minutes) < 60
    ? relativeFormat.format(minutes, 'minute')
    : relativeFormat.format(Math.round(minutes / 60), 'hour')
}
