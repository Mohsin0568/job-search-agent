import type { TailoredResume } from '../api/types'

/** How often to ask the server whether a resume being tailored is done. */
export const TAILORING_POLL_INTERVAL_MS = 3000

// The server gives up on a request left pending this long (TailoredResumeService.PENDING_TIMEOUT -
// keep in sync) and lets it be started again.
export const TAILORING_TIMEOUT_MS = 10 * 60 * 1000

export type TailoringState =
  /** The user has no resume to tailor. */
  | 'noResume'
  /** Never asked for. */
  | 'none'
  | 'tailoring'
  /** Pending for so long that the server has almost certainly lost it. */
  | 'stalled'
  | 'ready'
  /** Ready, but tailored from a resume the user has since replaced. */
  | 'outdated'
  | 'failed'

function pendingFor(tailored: TailoredResume, now: number): number {
  const requestedAt = new Date(tailored.requestedAt).getTime()
  return Number.isNaN(requestedAt) ? 0 : now - requestedAt
}

/** Whether the server is (still plausibly) working on it - which is when it is worth polling. */
export function isTailoring(tailored: TailoredResume, now = Date.now()): boolean {
  return tailored.status === 'PENDING' && pendingFor(tailored, now) < TAILORING_TIMEOUT_MS
}

export function tailoringState(
  tailored: TailoredResume | undefined,
  latestResumeVersion: number | null,
  now = Date.now(),
): TailoringState {
  if (latestResumeVersion === null) return 'noResume'
  if (!tailored) return 'none'
  switch (tailored.status) {
    case 'PENDING':
      return isTailoring(tailored, now) ? 'tailoring' : 'stalled'
    case 'FAILED':
      return 'failed'
    case 'READY':
      return tailored.resumeVersion === latestResumeVersion ? 'ready' : 'outdated'
  }
}
