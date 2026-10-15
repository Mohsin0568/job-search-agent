import type { ResumeReview } from '../api/types'

/** How often to ask the server whether a resume being reviewed is done. */
export const REVIEW_POLL_INTERVAL_MS = 3000

// The server gives up on a review left pending this long (ResumeReviewService.PENDING_TIMEOUT -
// keep in sync) and lets it be started again.
export const REVIEW_TIMEOUT_MS = 10 * 60 * 1000

export type ReviewState =
  /** The user has no resume to review. */
  | 'noResume'
  /** Never reviewed: found before reviews existed, or over the daily job's cap. */
  | 'none'
  | 'reviewing'
  /** Pending for so long that the server has almost certainly lost it. */
  | 'stalled'
  | 'ready'
  /** Ready, but of a resume the user has since replaced. */
  | 'outdated'
  | 'failed'

function pendingFor(review: ResumeReview, now: number): number {
  const requestedAt = new Date(review.requestedAt).getTime()
  return Number.isNaN(requestedAt) ? 0 : now - requestedAt
}

/** Whether the server is (still plausibly) working on it - which is when it is worth polling. */
export function isReviewing(review: ResumeReview, now = Date.now()): boolean {
  return review.status === 'PENDING' && pendingFor(review, now) < REVIEW_TIMEOUT_MS
}

export function reviewState(
  review: ResumeReview | undefined,
  latestResumeVersion: number | null,
  now = Date.now(),
): ReviewState {
  if (latestResumeVersion === null) return 'noResume'
  if (!review) return 'none'
  switch (review.status) {
    case 'PENDING':
      return isReviewing(review, now) ? 'reviewing' : 'stalled'
    case 'FAILED':
      return 'failed'
    case 'READY':
      return review.resumeVersion === latestResumeVersion ? 'ready' : 'outdated'
  }
}

function count(n: number, one: string, many: string): string {
  return `${n} ${n === 1 ? one : many}`
}

/** "7 missing keywords · 2 red flags", with "No …" for an empty list. */
export function findingsSummary(review: ResumeReview): string {
  const keywords = review.missingKeywords?.length ?? 0
  const redFlags = review.redFlags?.length ?? 0
  return [
    keywords === 0 ? 'No missing keywords' : count(keywords, 'missing keyword', 'missing keywords'),
    redFlags === 0 ? 'No red flags' : count(redFlags, 'red flag', 'red flags'),
  ].join(' · ')
}
