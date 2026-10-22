import type { JobSearchResult, ResumeReview } from '../api/types'
import { ActionMenu } from '../components/ActionMenu'
import { specified } from './format'
import { JobCard } from './JobCard'
import { JobStatusProblem, NotRelevantReasonForm } from './NotRelevantReasonForm'
import { markedOnText } from './status'
import { jobStatusMenuActions, useJobStatus } from './useJobStatus'

type Props = {
  result: JobSearchResult
  /** The job's resume review, if there is one: its findings can be added to a not-relevant reason. */
  review?: ResumeReview
  /** Called once the job has a new status (not after only its reason changed), with the job as it was. */
  onMoved: (previous: JobSearchResult, updated: JobSearchResult) => void
}

/**
 * A job on the Marked jobs page: when it was marked and why, with its status changes - the other mark,
 * editing the reason, back to the dashboard - in its ⋮ menu, as on the dashboard.
 */
export function MarkedJobCard({ result, review, onMoved }: Props) {
  const jobTitle = specified(result.job.jobTitle) ?? 'Untitled role'
  const status = useJobStatus(result, onMoved)
  const markedOn = markedOnText(result)

  return (
    <JobCard
      job={result.job}
      companyName={result.companyName}
      menu={<ActionMenu label={`More actions for ${jobTitle}`} actions={jobStatusMenuActions(status, jobTitle)} />}
    >
      <div className="space-y-3">
        {(markedOn || result.notRelevantReason) && (
          <div className="space-y-1">
            {markedOn && <p className="text-xs text-slate-500">{markedOn}</p>}
            {/* While it's being edited, the reason is in the form instead. */}
            {result.notRelevantReason && !status.editingReason && (
              <blockquote className="whitespace-pre-line break-words border-l-2 border-slate-200 pl-2 text-slate-700">
                <span className="sr-only">Your reason: </span>
                {result.notRelevantReason}
              </blockquote>
            )}
          </div>
        )}
        {status.editingReason ? (
          <NotRelevantReasonForm status={status} jobTitle={jobTitle} review={review} />
        ) : (
          <JobStatusProblem status={status} />
        )}
      </div>
    </JobCard>
  )
}
