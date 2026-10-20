import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useUpdateJobStatus } from '../api/hooks'
import type { JobSearchResult } from '../api/types'
import { specified } from './format'
import { movedMessage, STATUS_HOME } from './status'

const WHERE_IT_WENT = {
  ACTIVE: 'Go to dashboard',
  APPLIED: 'View applied jobs',
  NOT_RELEVANT: 'View not-relevant jobs',
} as const

export type MovedJob = { previous: JobSearchResult; updated: JobSearchResult }

type Props = {
  moved: MovedJob
  /** Whether to offer a link to where the job went - not when that's the page the user is on. */
  showWhereItWent: boolean
  onDismiss: () => void
}

/** After the user moves a job off a list: says where it went, and offers to undo it. */
export function JobMovedNotice({ moved, showWhereItWent, onDismiss }: Props) {
  const update = useUpdateJobStatus()
  const [problem, setProblem] = useState<string>()
  const { previous, updated } = moved
  const jobTitle = specified(updated.job.jobTitle) ?? 'Untitled role'

  // Puts back the reason too, so undoing a move off "not relevant" doesn't lose what the user wrote.
  const undo = async () => {
    setProblem(undefined)
    try {
      await update.mutateAsync({
        jobResultId: previous.id,
        status: previous.status,
        reason: previous.notRelevantReason ?? undefined,
      })
      onDismiss()
    } catch {
      setProblem('Couldn’t undo that. Please try again.')
    }
  }

  return (
    <div className="flex flex-wrap items-center gap-x-4 gap-y-2 rounded-lg border border-indigo-200 bg-indigo-50 px-4 py-3 text-sm">
      <div className="min-w-0 flex-1">
        <p role="status" className="text-indigo-900">
          {movedMessage(jobTitle, updated.companyName, updated.status)}
        </p>
        {problem && (
          <p role="alert" className="text-red-700">
            {problem}
          </p>
        )}
      </div>
      <div className="flex shrink-0 items-center gap-3">
        {showWhereItWent && (
          <Link to={STATUS_HOME[updated.status]} className="font-medium text-indigo-700 hover:underline">
            {WHERE_IT_WENT[updated.status]}
          </Link>
        )}
        <button
          type="button"
          onClick={() => void undo()}
          disabled={update.isPending}
          className="font-medium text-indigo-700 hover:underline disabled:opacity-60"
        >
          {update.isPending ? 'Undoing…' : 'Undo'}
        </button>
        <button
          type="button"
          onClick={onDismiss}
          aria-label="Dismiss"
          className="rounded px-1 text-indigo-500 hover:bg-indigo-100 hover:text-indigo-800"
        >
          <span aria-hidden="true">×</span>
        </button>
      </div>
    </div>
  )
}
