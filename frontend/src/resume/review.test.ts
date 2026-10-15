import { describe, expect, it } from 'vitest'
import { resumeReview } from '../test/fixtures'
import { findingsSummary, isReviewing, REVIEW_TIMEOUT_MS, reviewState } from './review'

const REQUESTED_AT = '2026-10-15T09:00:00Z'
const requested = new Date(REQUESTED_AT).getTime()
const pending = resumeReview({
  status: 'PENDING',
  resumeAtsScore: null,
  missingKeywords: null,
  redFlags: null,
  requestedAt: REQUESTED_AT,
  reviewedAt: null,
})

describe('reviewState', () => {
  it('is noResume when the user has nothing to review, whatever was reviewed before', () => {
    expect(reviewState(undefined, null)).toBe('noResume')
    expect(reviewState(resumeReview(), null)).toBe('noResume')
  })

  it('is none when the job was never reviewed', () => {
    expect(reviewState(undefined, 3)).toBe('none')
  })

  it('is reviewing while pending, and stalled once the server would have given up on it', () => {
    expect(reviewState(pending, 3, requested + 5_000)).toBe('reviewing')
    expect(reviewState(pending, 3, requested + REVIEW_TIMEOUT_MS - 1)).toBe('reviewing')
    expect(reviewState(pending, 3, requested + REVIEW_TIMEOUT_MS)).toBe('stalled')
  })

  it('is ready when it reviewed the current resume, and outdated when an earlier one', () => {
    expect(reviewState(resumeReview({ resumeVersion: 3 }), 3)).toBe('ready')
    expect(reviewState(resumeReview({ resumeVersion: 2 }), 3)).toBe('outdated')
  })

  it('is failed after a failure', () => {
    expect(reviewState(resumeReview({ status: 'FAILED', failureReason: 'no posting' }), 3)).toBe('failed')
  })
})

describe('isReviewing', () => {
  it('is true only for a pending review the server could still be working on', () => {
    expect(isReviewing(pending, requested + 1_000)).toBe(true)
    expect(isReviewing(pending, requested + REVIEW_TIMEOUT_MS)).toBe(false)
    expect(isReviewing(resumeReview({ status: 'READY' }), requested)).toBe(false)
    expect(isReviewing(resumeReview({ status: 'FAILED' }), requested)).toBe(false)
  })
})

describe('findingsSummary', () => {
  it('counts what the review found', () => {
    expect(findingsSummary(resumeReview({ missingKeywords: ['a', 'b', 'c'], redFlags: [] }))).toBe(
      '3 missing keywords · No red flags',
    )
    expect(
      findingsSummary(resumeReview({ missingKeywords: ['a'], redFlags: [{ issue: 'x', explanation: 'y' }] })),
    ).toBe('1 missing keyword · 1 red flag')
  })

  it('says so when there is nothing to report', () => {
    expect(findingsSummary(resumeReview({ missingKeywords: [], redFlags: [] }))).toBe(
      'No missing keywords · No red flags',
    )
  })
})
