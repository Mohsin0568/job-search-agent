import type { ReactNode } from 'react'
import type { JobListing } from '../api/types'
import { AtsBadge, ResumeAtsBadge } from './AtsBadge'
import { safeUrl, specified } from './format'

function Detail({ label, value }: { label: string; value: string | null }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-slate-500">{label}</dt>
      <dd className={`truncate ${value ? 'text-slate-800' : 'text-slate-400'}`} title={value ?? undefined}>
        {value ?? 'Not specified'}
      </dd>
    </div>
  )
}

type Props = {
  job: JobListing
  /** Shown above the title, where the card isn't already under its company's heading. */
  companyName?: string
  /** The score from reviewing the user's resume against this job, once there is one. */
  resumeAtsScore?: number | null
  /** Actions for this job, shown along the bottom of the card. */
  children?: ReactNode
}

export function JobCard({ job, companyName, resumeAtsScore, children }: Props) {
  const title = specified(job.jobTitle) ?? 'Untitled role'
  const url = safeUrl(job.url)
  const source = specified(job.source)

  return (
    <article className="flex flex-col rounded-lg border border-slate-200 bg-white p-4 shadow-sm transition hover:shadow-md">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          {companyName && <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{companyName}</p>}
          <h3 className="font-medium leading-snug text-slate-900">{title}</h3>
        </div>
        <div className="flex flex-col items-end gap-1">
          <AtsBadge score={job.atsScore} />
          {resumeAtsScore != null && <ResumeAtsBadge score={resumeAtsScore} />}
        </div>
      </div>

      <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
        <Detail label="Location" value={specified(job.location)} />
        <Detail label="Salary" value={specified(job.salaryRange)} />
        <Detail label="Posted" value={specified(job.datePosted)} />
        <Detail label="Closes" value={specified(job.lastDateForSubmission)} />
      </dl>

      <div className="mt-auto flex items-center justify-between gap-3 pt-4 text-sm">
        <span className="truncate text-xs text-slate-500">{source ? `via ${source}` : ''}</span>
        {url ? (
          <a
            href={url}
            target="_blank"
            rel="noopener noreferrer"
            className="shrink-0 font-medium text-indigo-600 hover:underline"
          >
            View posting<span aria-hidden="true"> ↗</span>
            <span className="sr-only"> for {title} (opens in a new tab)</span>
          </a>
        ) : (
          <span className="shrink-0 text-xs text-slate-400">No link available</span>
        )}
      </div>

      {children && <div className="mt-3 border-t border-slate-100 pt-3 text-sm">{children}</div>}
    </article>
  )
}
