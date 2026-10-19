import { describe, expect, it } from 'vitest'
import { formatDuration, formatRunStart, sourcesFailedText, totalsText } from './format'

describe('formatDuration', () => {
  it.each([
    ['2026-10-18T02:00:00Z', '2026-10-18T02:00:45Z', '45 sec'],
    ['2026-10-18T02:00:00Z', '2026-10-18T02:05:10Z', '5 min'],
    ['2026-10-18T02:00:00Z', '2026-10-18T03:00:00Z', '1 hr'],
    ['2026-10-18T02:00:00Z', '2026-10-18T03:05:00Z', '1 hr 5 min'],
  ])('from %s to %s is %s', (startedAt, finishedAt, expected) => {
    expect(formatDuration(startedAt, finishedAt)).toBe(expected)
  })

  it('gives up on times it cannot read, or that run backwards', () => {
    expect(formatDuration('not a time', '2026-10-18T02:00:00Z')).toBeNull()
    expect(formatDuration('2026-10-18T02:05:00Z', '2026-10-18T02:00:00Z')).toBeNull()
  })
})

describe('formatRunStart', () => {
  it('shows the day and time', () => {
    expect(formatRunStart('2026-10-18T02:00:00Z')).toMatch(/2026/)
  })

  it('passes through a value it cannot read', () => {
    expect(formatRunStart('soon')).toBe('soon')
  })
})

describe('totalsText', () => {
  it('puts the totals on one line', () => {
    expect(totalsText({ newJobs: 3, duplicateJobs: 1, filteredOutJobs: 0 })).toBe(
      '3 new · 1 already saved · 0 filtered out',
    )
  })
})

describe('sourcesFailedText', () => {
  it('counts sources', () => {
    expect(sourcesFailedText(1)).toBe('1 source failed')
    expect(sourcesFailedText(2)).toBe('2 sources failed')
  })
})
