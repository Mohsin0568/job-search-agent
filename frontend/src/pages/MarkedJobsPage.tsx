import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useMarkedJobs, useResumeReviews } from '../api/hooks'
import { specified } from '../jobs/format'
import { JobCard } from '../jobs/JobCard'
import { JobMovedNotice, type MovedJob } from '../jobs/JobMovedNotice'
import { JobStatusActions } from '../jobs/JobStatusActions'
import { MARKED_TABS, markedOnText, markedStatusForTab, type MarkedStatus } from '../jobs/status'

const EMPTY: Record<MarkedStatus, { title: string; text: string }> = {
  APPLIED: {
    title: 'No applications yet',
    text: 'Mark a job on your dashboard as applied and it moves here, so your dashboard only shows what’s left to do.',
  },
  NOT_RELEVANT: {
    title: 'Nothing marked not relevant',
    text: 'Jobs you mark as not relevant on your dashboard move here, with your reason if you gave one.',
  },
}

/** The jobs the user has applied for or set aside, last marked first, in a tab each. */
export function MarkedJobsPage() {
  const [searchParams] = useSearchParams()
  const status = markedStatusForTab(searchParams.get('tab'))
  const [moved, setMoved] = useState<MovedJob>()

  return (
    <div>
      <h1 className="text-2xl font-semibold">Marked jobs</h1>
      <p className="mt-1 text-slate-500">
        Jobs you’ve applied for or marked as not relevant. They no longer show on your dashboard.
      </p>

      <nav aria-label="Marked jobs" className="mt-6 flex gap-1 border-b border-slate-200">
        {MARKED_TABS.map((tab) => {
          const current = tab.status === status
          return (
            <Link
              key={tab.status}
              to={tab.tab ? `?tab=${tab.tab}` : '.'}
              aria-current={current ? 'page' : undefined}
              onClick={() => setMoved(undefined)}
              className={`-mb-px border-b-2 px-3 py-2 text-sm font-medium ${
                current
                  ? 'border-indigo-600 text-indigo-700'
                  : 'border-transparent text-slate-600 hover:border-slate-300 hover:text-slate-900'
              }`}
            >
              {tab.label}
            </Link>
          )
        })}
      </nav>

      {moved && (
        <div className="mt-6">
          <JobMovedNotice key={moved.updated.id} moved={moved} showWhereItWent onDismiss={() => setMoved(undefined)} />
        </div>
      )}

      {/* Keyed, so switching tabs starts the other list afresh rather than showing this one's state. */}
      <MarkedJobsList key={status} status={status} onMoved={setMoved} />
    </div>
  )
}

function MarkedJobsList({ status, onMoved }: { status: MarkedStatus; onMoved: (moved: MovedJob) => void }) {
  const jobs = useMarkedJobs(status)
  const resumeReviews = useResumeReviews()
  const loaded = jobs.data?.pages.flat() ?? []
  const reviews = new Map((resumeReviews.data ?? []).map((review) => [review.jobResultId, review]))

  return (
    <div className="mt-6">
      {jobs.isPending ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3" aria-busy="true" aria-label="Loading your marked jobs">
          {Array.from({ length: 3 }, (_, i) => (
            <div key={i} className="h-44 animate-pulse rounded-lg bg-slate-200" />
          ))}
        </div>
      ) : jobs.isError ? (
        <div className="rounded-xl border border-red-200 bg-red-50 p-6 text-center">
          <p className="font-medium text-red-800">We couldn’t load these jobs</p>
          <p className="mt-1 text-sm text-red-700">{jobs.error.message}</p>
          <button
            type="button"
            onClick={() => void jobs.refetch()}
            disabled={jobs.isFetching}
            className="mt-4 rounded-md border border-red-300 bg-white px-3 py-1.5 text-sm font-medium text-red-700 hover:bg-red-100 disabled:opacity-60"
          >
            {jobs.isFetching ? 'Retrying…' : 'Try again'}
          </button>
        </div>
      ) : loaded.length === 0 ? (
        <div className="rounded-xl border border-dashed border-slate-300 bg-white p-10 text-center">
          <p className="font-medium text-slate-800">{EMPTY[status].title}</p>
          <p className="mt-1 text-sm text-slate-500">{EMPTY[status].text}</p>
        </div>
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {loaded.map((result) => {
              const jobTitle = specified(result.job.jobTitle) ?? 'Untitled role'
              const markedOn = markedOnText(result)
              return (
                <JobCard key={result.id} job={result.job} companyName={result.companyName}>
                  <div className="space-y-3">
                    {(markedOn || result.notRelevantReason) && (
                      <div className="space-y-1">
                        {markedOn && <p className="text-xs text-slate-500">{markedOn}</p>}
                        {result.notRelevantReason && (
                          <blockquote className="whitespace-pre-line break-words border-l-2 border-slate-200 pl-2 text-slate-700">
                            <span className="sr-only">Your reason: </span>
                            {result.notRelevantReason}
                          </blockquote>
                        )}
                      </div>
                    )}
                    <JobStatusActions
                      result={result}
                      jobTitle={jobTitle}
                      review={reviews.get(result.id)}
                      onMoved={(previous, updated) => onMoved({ previous, updated })}
                    />
                  </div>
                </JobCard>
              )
            })}
          </div>
          {jobs.hasNextPage && (
            <div className="mt-4 text-center">
              <button
                type="button"
                onClick={() => void jobs.fetchNextPage()}
                disabled={jobs.isFetchingNextPage}
                className="rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100 disabled:opacity-60"
              >
                {jobs.isFetchingNextPage ? 'Loading…' : 'Show more'}
              </button>
            </div>
          )}
          {jobs.isFetchNextPageError && (
            <p role="alert" className="mt-2 text-center text-sm text-red-700">
              We couldn’t load more jobs. Please try again.
            </p>
          )}
        </>
      )}
    </div>
  )
}
