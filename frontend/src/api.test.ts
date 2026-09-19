import { describe, expect, it } from 'vitest'
import { outcomeLabel, percent, points } from './api'

describe('formatters', () => {
  it('formats probabilities as percentages with one decimal', () => {
    expect(percent(0.2736)).toBe('27.4%')
    expect(percent(1)).toBe('100.0%')
  })

  it('formats edges as signed percentage points', () => {
    expect(points(0.043)).toBe('+4.3 pts')
    expect(points(-0.05)).toBe('−5.0 pts')
  })

  it('names outcomes after the team that wins', () => {
    expect(outcomeLabel('HOME', 'Fulham', 'Manchester United')).toBe('Fulham win')
    expect(outcomeLabel('AWAY', 'Fulham', 'Manchester United')).toBe('Manchester United win')
    expect(outcomeLabel('DRAW', 'Fulham', 'Manchester United')).toBe('Draw')
  })
})
