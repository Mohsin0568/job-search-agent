import type { JobListing } from '../api/types'
import { AtsBadge } from './AtsBadge'
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

export function JobCard({ job }: { job: JobListing }) {
  const title = specified(job.jobTitle) ?? 'Untitled role'
  const url = safeUrl(job.url)
  const source = specified(job.source)

  return (
    <article className="flex flex-col rounded-lg border border-slate-200 bg-white p-4 shadow-sm transition hover:shadow-md">
      <div className="flex items-start justify-between gap-3">
        <h3 className="font-medium leading-snug text-slate-900">{title}</h3>
        <AtsBadge score={job.atsScore} />
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
    </article>
  )
}
