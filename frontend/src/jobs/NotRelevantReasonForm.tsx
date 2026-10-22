import { useId, useState } from 'react'
import { MAX_NOT_RELEVANT_REASON_LENGTH, type ResumeReview } from '../api/types'
import { reasonSuggestions, withPhrase } from './status'
import type { JobStatusControl } from './useJobStatus'

const buttonClass =
  'rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 shadow-sm transition hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-indigo-200 disabled:cursor-not-allowed disabled:opacity-60'
const primaryButtonClass =
  'rounded-md bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white shadow-sm transition hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-200 disabled:cursor-not-allowed disabled:opacity-60'

// Show how much room is left only once the user is close to running out.
const SHOW_COUNT_FROM = MAX_NOT_RELEVANT_REASON_LENGTH - 100

/** Why the last status change failed, if it did. */
export function JobStatusProblem({ status }: { status: JobStatusControl }) {
  if (!status.problem) return null
  return (
    <p role="alert" className="text-red-700">
      {status.problem}
    </p>
  )
}

type FormProps = {
  status: JobStatusControl
  jobTitle: string
  review?: ResumeReview
}

/** Asks why a job isn't relevant - optional, with phrases from its resume review - then marks it so. */
export function NotRelevantReasonForm({ status, jobTitle, review }: FormProps) {
  const [reason, setReason] = useState(status.result.notRelevantReason ?? '')
  const reasonId = useId()
  const suggestions = reasonSuggestions(review).filter((phrase) => !reason.includes(phrase))
  const alreadyNotRelevant = status.result.status === 'NOT_RELEVANT'

  return (
    <form
      className="space-y-2"
      aria-label={`Why ${jobTitle} isn’t relevant`}
      onSubmit={(event) => {
        event.preventDefault()
        void status.mark('NOT_RELEVANT', reason.trim() || undefined)
      }}
    >
      <label htmlFor={reasonId} className="block text-xs font-medium text-slate-600">
        Why isn’t it relevant? <span className="font-normal text-slate-400">(optional)</span>
      </label>
      <textarea
        id={reasonId}
        value={reason}
        onChange={(event) => setReason(event.target.value)}
        maxLength={MAX_NOT_RELEVANT_REASON_LENGTH}
        rows={3}
        autoFocus
        className="block w-full rounded-md border border-slate-300 px-2.5 py-1.5 text-sm shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-200"
        placeholder="e.g. Needs Kubernetes, which I haven’t used"
      />
      {reason.length >= SHOW_COUNT_FROM && (
        <p className="text-xs text-slate-500">{MAX_NOT_RELEVANT_REASON_LENGTH - reason.length} characters left</p>
      )}
      {suggestions.length > 0 && (
        <div>
          <p className="text-xs text-slate-500">Add from the resume review:</p>
          <ul className="mt-1 flex flex-wrap gap-1.5">
            {suggestions.map((phrase) => (
              <li key={phrase}>
                <button
                  type="button"
                  onClick={() => setReason((current) => withPhrase(current, phrase).slice(0, MAX_NOT_RELEVANT_REASON_LENGTH))}
                  aria-label={`Add “${phrase}” to the reason`}
                  className="rounded-full border border-slate-200 bg-slate-50 px-2 py-0.5 text-xs text-slate-700 hover:border-indigo-300 hover:bg-indigo-50"
                >
                  + {phrase}
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
      <div className="flex flex-wrap gap-2">
        <button type="submit" disabled={status.saving} className={primaryButtonClass}>
          {status.saving ? 'Saving…' : alreadyNotRelevant ? 'Save reason' : 'Mark not relevant'}
        </button>
        <button type="button" onClick={status.closeReason} disabled={status.saving} className={buttonClass}>
          Cancel
        </button>
      </div>
      <JobStatusProblem status={status} />
    </form>
  )
}
