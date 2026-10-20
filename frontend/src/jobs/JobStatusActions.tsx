import { useId, useState } from 'react'
import { useUpdateJobStatus } from '../api/hooks'
import { MAX_NOT_RELEVANT_REASON_LENGTH, type JobSearchResult, type JobStatus, type ResumeReview } from '../api/types'
import { reasonSuggestions, withPhrase } from './status'

const buttonClass =
  'rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 shadow-sm transition hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-indigo-200 disabled:cursor-not-allowed disabled:opacity-60'
const primaryButtonClass =
  'rounded-md bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white shadow-sm transition hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-200 disabled:cursor-not-allowed disabled:opacity-60'

// Show how much room is left only once the user is close to running out.
const SHOW_COUNT_FROM = MAX_NOT_RELEVANT_REASON_LENGTH - 100

type Props = {
  result: JobSearchResult
  /** Names the job in each button's accessible name, so a screen reader can tell the cards apart. */
  jobTitle: string
  /** The job's resume review, if there is one: its findings can be added to a not-relevant reason. */
  review?: ResumeReview
  /** Called once the job has a new status (not after only its reason changed), with the job as it was. */
  onMoved?: (previous: JobSearchResult, updated: JobSearchResult) => void
}

/** On a job card: mark it as applied for or not relevant, or put it back on the dashboard. */
export function JobStatusActions({ result, jobTitle, review, onMoved }: Props) {
  const update = useUpdateJobStatus()
  const [editingReason, setEditingReason] = useState(false)
  const [reason, setReason] = useState('')
  const [problem, setProblem] = useState<string>()
  const reasonId = useId()

  const mark = async (status: JobStatus, withReason?: string) => {
    setProblem(undefined)
    try {
      const updated = await update.mutateAsync({ jobResultId: result.id, status, reason: withReason })
      setEditingReason(false)
      if (updated.status !== result.status) onMoved?.(result, updated)
    } catch {
      setProblem('Couldn’t update this job. Please try again.')
    }
  }

  const openReason = () => {
    setProblem(undefined)
    setReason(result.notRelevantReason ?? '')
    setEditingReason(true)
  }

  const action = (label: string, accessibleLabel: string, onClick: () => void) => (
    <button type="button" onClick={onClick} disabled={update.isPending} aria-label={accessibleLabel} className={buttonClass}>
      {label}
    </button>
  )

  if (editingReason) {
    const suggestions = reasonSuggestions(review).filter((phrase) => !reason.includes(phrase))
    const alreadyNotRelevant = result.status === 'NOT_RELEVANT'
    return (
      <form
        className="space-y-2"
        aria-label={`Why ${jobTitle} isn’t relevant`}
        onSubmit={(event) => {
          event.preventDefault()
          void mark('NOT_RELEVANT', reason.trim() || undefined)
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
          <p className="text-xs text-slate-500">
            {MAX_NOT_RELEVANT_REASON_LENGTH - reason.length} characters left
          </p>
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
          <button type="submit" disabled={update.isPending} className={primaryButtonClass}>
            {update.isPending ? 'Saving…' : alreadyNotRelevant ? 'Save reason' : 'Mark not relevant'}
          </button>
          <button type="button" onClick={() => setEditingReason(false)} disabled={update.isPending} className={buttonClass}>
            Cancel
          </button>
        </div>
        {problem && (
          <p role="alert" className="text-red-700">
            {problem}
          </p>
        )}
      </form>
    )
  }

  return (
    <div className="space-y-2">
      <div className="flex flex-wrap gap-2">
        {result.status !== 'APPLIED' &&
          action('Applied', `Mark ${jobTitle} as applied`, () => void mark('APPLIED'))}
        {result.status === 'NOT_RELEVANT'
          ? action('Edit reason', `Edit why ${jobTitle} isn’t relevant`, openReason)
          : action('Not relevant', `Mark ${jobTitle} as not relevant`, openReason)}
        {result.status !== 'ACTIVE' &&
          action('Back to dashboard', `Move ${jobTitle} back to your dashboard`, () => void mark('ACTIVE'))}
      </div>
      {problem && (
        <p role="alert" className="text-red-700">
          {problem}
        </p>
      )}
    </div>
  )
}
