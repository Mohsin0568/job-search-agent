import { describe, expect, it } from 'vitest'
import { tailoredResume } from '../test/fixtures'
import { isTailoring, TAILORING_TIMEOUT_MS, tailoringState } from './tailoring'

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
