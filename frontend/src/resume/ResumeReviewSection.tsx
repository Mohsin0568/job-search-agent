import { useState } from 'react'
import { useRequestResumeReview } from '../api/hooks'
import type { ResumeReview } from '../api/types'
import { findingsSummary, reviewState } from './review'

const secondaryButtonClass =
  'rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 shadow-sm transition hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-indigo-200 disabled:cursor-not-allowed disabled:opacity-60'

// The server's reasons start in lower case, to follow "we couldn't…" in a sentence. Here they stand alone.
function sentence(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1)
}

type Props = {
  jobResultId: string
  /** Names the job in each button's accessible name, so a screen reader can tell the cards apart. */
  jobTitle: string
  /** The review of the user's resume for this job, if there has ever been one. */
  review: ResumeReview | undefined
  /** The version of the user's current resume, or null if they haven't uploaded one. */
  latestResumeVersion: number | null
}

/** What a review found: a summary line that opens to the missing keywords and the red flags. */
function Findings({ review }: { review: ResumeReview }) {
  const keywords = review.missingKeywords ?? []
  const redFlags = review.redFlags ?? []
  const summary = findingsSummary(review)

  if (keywords.length === 0 && redFlags.length === 0) {
    return <p className="text-slate-600">{summary}</p>
  }
  return (
    <details className="group">
      <summary className="cursor-pointer text-slate-700 marker:text-slate-400 hover:text-slate-900">{summary}</summary>
      <div className="mt-2 space-y-3">
        <div>
          <h4 className="text-xs font-medium text-slate-500">Missing keywords</h4>
          {keywords.length === 0 ? (
            <p className="mt-1 text-slate-600">No missing keywords</p>
          ) : (
            <ul className="mt-1 flex flex-wrap gap-1.5" aria-label="Missing keywords">
              {keywords.map((keyword) => (
                <li key={keyword} className="rounded bg-slate-100 px-2 py-0.5 text-xs text-slate-700">
                  {keyword}
                </li>
              ))}
            </ul>
          )}
        </div>
        <div>
          <h4 className="text-xs font-medium text-slate-500">Red flags</h4>
          {redFlags.length === 0 ? (
            <p className="mt-1 text-slate-600">No red flags</p>
          ) : (
            <ul className="mt-1 space-y-1.5" aria-label="Red flags">
              {redFlags.map((flag) => (
                <li key={flag.issue}>
                  <span className="font-medium text-slate-800">{flag.issue}</span>
                  <span className="text-slate-600"> - {flag.explanation}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </details>
  )
}

/** On a job card: how the user's resume reads to this company's recruiter, and a way to (re)review it. */
export function ResumeReviewSection({ jobResultId, jobTitle, review, latestResumeVersion }: Props) {
  const request = useRequestResumeReview()
  const [problem, setProblem] = useState<string>()

  const state = reviewState(review, latestResumeVersion)

  const onReview = async () => {
    setProblem(undefined)
    try {
      await request.mutateAsync(jobResultId)
    } catch (error) {
      setProblem(error instanceof Error ? sentence(error.message) : 'Couldn’t start the review. Please try again.')
    }
  }

  // "Try again" alone would read the same as the tailoring action's button on the same card.
  const reviewButton = (label: string, accessibleLabel = label) => (
    <button
      type="button"
      onClick={() => void onReview()}
      disabled={request.isPending}
      aria-label={`${accessibleLabel} for ${jobTitle}`}
      className={secondaryButtonClass}
    >
      {request.isPending ? 'Starting…' : label}
    </button>
  )

  // Without a resume there's nothing to review; the tailoring action already asks for one.
  if (state === 'noResume') return null

  return (
    <div className="space-y-2">
      {state === 'none' && (
        <div className="flex flex-wrap items-center gap-2">
          {reviewButton('Review resume')}
          <span className="text-xs text-slate-500">See your resume as this company’s recruiter would.</span>
        </div>
      )}

      {state === 'reviewing' && (
        <p role="status" className="flex items-center gap-2 text-slate-600">
          <span className="h-4 w-4 animate-spin rounded-full border-2 border-indigo-200 border-t-indigo-600" />
          Reviewing your resume for this job…
        </p>
      )}

      {state === 'ready' && review && <Findings review={review} />}

      {state === 'outdated' && review && (
        <>
          <p className="text-xs text-slate-500">Reviewed against an earlier version of your resume.</p>
          <Findings review={review} />
          {reviewButton('Review again')}
        </>
      )}

      {state === 'failed' && (
        <>
          <p className="text-red-700">
            {sentence(review?.failureReason ?? 'something went wrong while reviewing your resume')}.
          </p>
          {reviewButton('Try again', 'Try the review again')}
        </>
      )}

      {state === 'stalled' && (
        <>
          <p className="text-slate-600">The review is taking longer than it should.</p>
          {reviewButton('Try again', 'Try the review again')}
        </>
      )}

      {problem && (
        <p role="alert" className="text-red-700">
          {problem}
        </p>
      )}
    </div>
  )
}
