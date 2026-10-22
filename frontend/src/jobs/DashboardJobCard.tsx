import type { JobSearchResult, ResumeReview, ResumeTemplate, TailoredResume } from '../api/types'
import { ActionMenu } from '../components/ActionMenu'
import { ResumeReviewSection } from '../resume/ResumeReviewSection'
import { TailoredResumeView } from '../resume/TailoredResumeAction'
import { tailoringMenuActions, useTailoring } from '../resume/useTailoring'
import { specified } from './format'
import { JobCard } from './JobCard'
import { JobStatusProblem, NotRelevantReasonForm } from './NotRelevantReasonForm'
import { jobStatusMenuActions, useJobStatus } from './useJobStatus'

type Props = {
  result: JobSearchResult
  review: ResumeReview | undefined
  /** The version of the user's current resume (null if none), or undefined while it can't be known. */
  latestResumeVersion: number | null | undefined
  /** Whether the user's reviews have loaded, so this job's review (or lack of one) is known. */
  reviewsLoaded: boolean
  reviewsUnavailable: boolean
  /** This job's tailoring, once the user's tailored resumes have loaded. */
  tailoring: { tailored: TailoredResume | undefined; defaultTemplate: ResumeTemplate } | undefined
  tailoringUnavailable: boolean
  onMoved: (previous: JobSearchResult, updated: JobSearchResult) => void
}

/**
 * A job on the dashboard: its resume review and tailoring on the card, and the actions used less
 * often - marking it applied or not relevant, tailoring with fixes or as is - in its ⋮ menu.
 */
export function DashboardJobCard({
  result,
  review,
  latestResumeVersion,
  reviewsLoaded,
  reviewsUnavailable,
  tailoring,
  tailoringUnavailable,
  onMoved,
}: Props) {
  const jobTitle = specified(result.job.jobTitle) ?? 'Untitled role'
  const status = useJobStatus(result, onMoved)
  // Always run, so hooks keep their order; until tailoring has loaded it reads as "no resume" and offers nothing.
  const tailoringControl = useTailoring({
    jobResultId: result.id,
    tailored: tailoring?.tailored,
    latestResumeVersion: tailoring ? (latestResumeVersion ?? null) : null,
    defaultTemplate: tailoring?.defaultTemplate ?? 'CLASSIC',
    review,
  })

  const menuActions = [
    ...jobStatusMenuActions(status, jobTitle),
    ...(tailoring ? tailoringMenuActions(tailoringControl, jobTitle) : []),
  ]

  return (
    <JobCard
      job={result.job}
      resumeAtsScore={review?.status === 'READY' ? review.resumeAtsScore : null}
      menu={<ActionMenu label={`More actions for ${jobTitle}`} actions={menuActions} />}
    >
      <div className="space-y-3">
        {reviewsLoaded && latestResumeVersion !== undefined ? (
          <ResumeReviewSection
            jobResultId={result.id}
            jobTitle={jobTitle}
            review={review}
            latestResumeVersion={latestResumeVersion}
          />
        ) : reviewsUnavailable ? (
          <p className="text-xs text-slate-400">Resume review is unavailable right now.</p>
        ) : null}
        {tailoring ? (
          <TailoredResumeView tailoring={tailoringControl} jobTitle={jobTitle} fixActionsInMenu />
        ) : tailoringUnavailable ? (
          <p className="text-xs text-slate-400">Resume tailoring is unavailable right now.</p>
        ) : null}
        {status.editingReason ? (
          <div className="border-t border-slate-100 pt-3">
            <NotRelevantReasonForm status={status} jobTitle={jobTitle} review={review} />
          </div>
        ) : (
          <JobStatusProblem status={status} />
        )}
      </div>
    </JobCard>
  )
}
