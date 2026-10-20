import type { JobSearchResult, JobStatus, ResumeReview } from '../api/types'

export type MarkedStatus = Exclude<JobStatus, 'ACTIVE'>

/** Where each status lives in the app. */
export const STATUS_HOME: Record<JobStatus, string> = {
  ACTIVE: '/',
  APPLIED: '/marked',
  NOT_RELEVANT: '/marked?tab=not-relevant',
}

/** The tab of the marked jobs page that lists each marked status, as it appears in ?tab=. */
export const MARKED_TABS: { status: MarkedStatus; tab: string | null; label: string }[] = [
  { status: 'APPLIED', tab: null, label: 'Applied' },
  { status: 'NOT_RELEVANT', tab: 'not-relevant', label: 'Not relevant' },
]

export function markedStatusForTab(tab: string | null): MarkedStatus {
  return MARKED_TABS.find((candidate) => candidate.tab === tab)?.status ?? 'APPLIED'
}

/** "“Backend Engineer” at Acme Corp is marked as applied." - after the user moves a job. */
export function movedMessage(jobTitle: string, companyName: string, to: JobStatus): string {
  const where =
    to === 'ACTIVE' ? 'is back on your dashboard' : to === 'APPLIED' ? 'is marked as applied' : 'is marked as not relevant'
  return `“${jobTitle}” at ${companyName} ${where}.`
}

/** "Applied on 20 Oct 2026", or null when the job has never been marked. */
export function markedOnText(result: JobSearchResult): string | null {
  if (result.status === 'ACTIVE' || !result.statusChangedAt) return null
  const date = new Date(result.statusChangedAt)
  if (Number.isNaN(date.getTime())) return null
  const day = date.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
  return `${result.status === 'APPLIED' ? 'Applied' : 'Marked not relevant'} on ${day}`
}

// A review has at most 20 keywords; a handful is plenty to pick from.
const MAX_KEYWORD_SUGGESTIONS = 6

/** Phrases from the job's resume review the user can add to a not-relevant reason with one click. */
export function reasonSuggestions(review: ResumeReview | undefined): string[] {
  if (review?.status !== 'READY') return []
  const redFlags = (review.redFlags ?? []).map((flag) => flag.issue)
  const keywords = (review.missingKeywords ?? []).slice(0, MAX_KEYWORD_SUGGESTIONS).map((keyword) => `Missing ${keyword}`)
  return [...new Set([...redFlags, ...keywords])]
}

/** The reason with the phrase added: "Too senior" + "Missing Kubernetes" -> "Too senior; Missing Kubernetes". */
export function withPhrase(reason: string, phrase: string): string {
  const trimmed = reason.trimEnd().replace(/[;,.]$/, '')
  return trimmed ? `${trimmed}; ${phrase}` : phrase
}
