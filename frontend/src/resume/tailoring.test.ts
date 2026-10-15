import { describe, expect, it } from 'vitest'
import { resumeReview, tailoredResume } from '../test/fixtures'
import {
  fixableReview,
  fixSummary,
  isTailoring,
  preferredTemplate,
  TAILORING_TIMEOUT_MS,
  tailoringState,
  templateLabel,
} from './tailoring'

const REQUESTED_AT = '2026-10-13T09:00:00Z'
const requested = new Date(REQUESTED_AT).getTime()
const pending = tailoredResume({ status: 'PENDING', requestedAt: REQUESTED_AT, completedAt: null })

describe('tailoringState', () => {
  it('is noResume when the user has nothing to tailor, whatever was tailored before', () => {
    expect(tailoringState(undefined, null)).toBe('noResume')
    expect(tailoringState(tailoredResume({ status: 'READY' }), null)).toBe('noResume')
  })

  it('is none when tailoring was never asked for', () => {
    expect(tailoringState(undefined, 3)).toBe('none')
  })

  it('is tailoring while a request is pending, and stalled once the server would have given up on it', () => {
    expect(tailoringState(pending, 3, requested + 5_000)).toBe('tailoring')
    expect(tailoringState(pending, 3, requested + TAILORING_TIMEOUT_MS - 1)).toBe('tailoring')
    expect(tailoringState(pending, 3, requested + TAILORING_TIMEOUT_MS)).toBe('stalled')
  })

  it('is ready when tailored from the current resume, and outdated when from an earlier one', () => {
    expect(tailoringState(tailoredResume({ status: 'READY', resumeVersion: 3 }), 3)).toBe('ready')
    expect(tailoringState(tailoredResume({ status: 'READY', resumeVersion: 2 }), 3)).toBe('outdated')
  })

  it('is failed after a failure', () => {
    expect(tailoringState(tailoredResume({ status: 'FAILED', failureReason: 'no posting' }), 3)).toBe('failed')
  })
})

describe('isTailoring', () => {
  it('is true only for a pending request the server could still be working on', () => {
    expect(isTailoring(pending, requested + 1_000)).toBe(true)
    expect(isTailoring(pending, requested + TAILORING_TIMEOUT_MS)).toBe(false)
    expect(isTailoring(tailoredResume({ status: 'READY' }), requested)).toBe(false)
    expect(isTailoring(tailoredResume({ status: 'FAILED' }), requested)).toBe(false)
  })

  it('treats a request with an unreadable time as just started', () => {
    expect(isTailoring({ ...pending, requestedAt: 'not a date' })).toBe(true)
  })
})

describe('preferredTemplate', () => {
  it('is the template of the most recently requested tailored resume', () => {
    expect(
      preferredTemplate([
        tailoredResume({ jobResultId: 'r-1', template: 'COMPACT', requestedAt: '2026-10-13T09:00:00Z' }),
        tailoredResume({ jobResultId: 'r-2', template: 'MODERN', requestedAt: '2026-10-14T09:00:00.5Z' }),
        tailoredResume({ jobResultId: 'r-3', template: 'CLASSIC', requestedAt: '2026-10-14T09:00:00Z' }),
      ]),
    ).toBe('MODERN')
  })

  it('is Classic before anything has been tailored', () => {
    expect(preferredTemplate([])).toBe('CLASSIC')
  })
})

describe('templateLabel', () => {
  it('names each template for people', () => {
    expect(templateLabel('CLASSIC')).toBe('Classic')
    expect(templateLabel('MODERN')).toBe('Modern')
    expect(templateLabel('COMPACT')).toBe('Compact')
    expect(templateLabel('EVEN')).toBe('Even')
    expect(templateLabel('STACK_OVERFLOW')).toBe('Stack Overflow')
    expect(templateLabel('KENDALL')).toBe('Kendall')
  })
})

describe('fixableReview', () => {
  it('is a finished review of the latest resume that found something', () => {
    const review = resumeReview({ resumeVersion: 3 })
    expect(fixableReview(review, 3)).toBe(review)
    expect(fixableReview(resumeReview({ missingKeywords: [], redFlags: [] }), 3)).toBeUndefined()
    expect(fixableReview(resumeReview({ missingKeywords: ['Go'], redFlags: [] }), 3)).toBeDefined()
  })

  it('is undefined for no review, one still running or failed, or one of an older resume', () => {
    expect(fixableReview(undefined, 3)).toBeUndefined()
    expect(fixableReview(resumeReview({ status: 'PENDING' }), 3)).toBeUndefined()
    expect(fixableReview(resumeReview({ status: 'FAILED' }), 3)).toBeUndefined()
    expect(fixableReview(resumeReview({ resumeVersion: 2 }), 3)).toBeUndefined()
    expect(fixableReview(resumeReview(), null)).toBeUndefined()
  })
})

describe('fixSummary', () => {
  const flag = { issue: 'Short tenures', explanation: 'Three jobs in two years.' }

  it('says what a tailored resume fixes', () => {
    expect(fixSummary({ keywords: ['Kubernetes', 'Terraform'], redFlags: [flag] })).toBe(
      'Adds Kubernetes, Terraform · Addresses 1 red flag',
    )
    expect(fixSummary({ keywords: [], redFlags: [flag, flag] })).toBe('Addresses 2 red flags')
    expect(fixSummary({ keywords: ['Go'], redFlags: [] })).toBe('Adds Go')
  })

  it('is null for one tailored as is', () => {
    expect(fixSummary(null)).toBeNull()
    expect(fixSummary({ keywords: [], redFlags: [] })).toBeNull()
  })
})
