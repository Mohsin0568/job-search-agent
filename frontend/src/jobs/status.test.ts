import { describe, expect, it } from 'vitest'
import { result, resumeReview } from '../test/fixtures'
import { markedOnText, markedStatusForTab, movedMessage, reasonSuggestions, withPhrase } from './status'

describe('markedStatusForTab', () => {
  it('lists applied jobs unless the not-relevant tab is asked for', () => {
    expect(markedStatusForTab(null)).toBe('APPLIED')
    expect(markedStatusForTab('not-relevant')).toBe('NOT_RELEVANT')
    expect(markedStatusForTab('something-else')).toBe('APPLIED')
  })
})

describe('movedMessage', () => {
  it('says where the job went', () => {
    expect(movedMessage('Backend Engineer', 'Acme Corp', 'APPLIED')).toBe(
      '“Backend Engineer” at Acme Corp is marked as applied.',
    )
    expect(movedMessage('Backend Engineer', 'Acme Corp', 'NOT_RELEVANT')).toBe(
      '“Backend Engineer” at Acme Corp is marked as not relevant.',
    )
    expect(movedMessage('Backend Engineer', 'Acme Corp', 'ACTIVE')).toBe(
      '“Backend Engineer” at Acme Corp is back on your dashboard.',
    )
  })
})

describe('markedOnText', () => {
  it('says when the job was marked', () => {
    const applied = result('r-1', 'Acme Corp', {}, { status: 'APPLIED', statusChangedAt: '2026-10-20T09:00:00Z' })
    expect(markedOnText(applied)).toMatch(/^Applied on .*2026$/)
    expect(markedOnText({ ...applied, status: 'NOT_RELEVANT' })).toMatch(/^Marked not relevant on .*2026$/)
  })

  it('says nothing for a job on the dashboard or one with no usable date', () => {
    expect(markedOnText(result('r-1', 'Acme Corp', {}, { statusChangedAt: '2026-10-20T09:00:00Z' }))).toBeNull()
    expect(markedOnText(result('r-1', 'Acme Corp', {}, { status: 'APPLIED' }))).toBeNull()
    expect(markedOnText(result('r-1', 'Acme Corp', {}, { status: 'APPLIED', statusChangedAt: 'soon' }))).toBeNull()
  })
})

describe('reasonSuggestions', () => {
  it('offers the review’s red flags, then its first few missing keywords', () => {
    const review = resumeReview({
      missingKeywords: ['Kubernetes', 'Terraform', 'Go', 'Kafka', 'gRPC', 'AWS', 'Rust'],
      redFlags: [{ issue: 'No measurable results', explanation: 'Bullets list duties.' }],
    })

    expect(reasonSuggestions(review)).toEqual([
      'No measurable results',
      'Missing Kubernetes',
      'Missing Terraform',
      'Missing Go',
      'Missing Kafka',
      'Missing gRPC',
      'Missing AWS',
    ])
  })

  it('offers nothing until the review is ready', () => {
    expect(reasonSuggestions(undefined)).toEqual([])
    expect(reasonSuggestions(resumeReview({ status: 'PENDING' }))).toEqual([])
    expect(reasonSuggestions(resumeReview({ missingKeywords: [], redFlags: [] }))).toEqual([])
  })
})

describe('withPhrase', () => {
  it('adds the phrase after whatever the user has written', () => {
    expect(withPhrase('', 'Missing Kubernetes')).toBe('Missing Kubernetes')
    expect(withPhrase('Too senior', 'Missing Kubernetes')).toBe('Too senior; Missing Kubernetes')
    expect(withPhrase('Too senior. ', 'Missing Kubernetes')).toBe('Too senior; Missing Kubernetes')
  })
})
