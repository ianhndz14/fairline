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
