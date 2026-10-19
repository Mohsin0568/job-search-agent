import { useState } from 'react'
import type { JobSearchRunSummary } from '../api/types'
import { formatDuration, formatRunStart, RUN_STATUS_CLASS, RUN_STATUS_LABEL, sourcesFailedText, totalsText } from './format'
import { RunDetails } from './RunDetails'

type Props = {
  run: JobSearchRunSummary
  /** How many columns the table has, for the opened row to span. */
  columns: number
}

// Too narrow for the count columns on a phone: the status cell shows the totals there instead.
const numberCell = 'hidden px-4 py-3 text-right tabular-nums sm:table-cell'

/** One run of the search history: its totals, and when opened, what it found for each company. */
export function SearchRunRows({ run, columns }: Props) {
  const [open, setOpen] = useState(false)
  const detailsId = `search-run-${run.runId}`
  const started = formatRunStart(run.startedAt)
  const duration = formatDuration(run.startedAt, run.finishedAt)

  return (
    // A tbody per run keeps its row and its opened details together.
    <tbody className="border-t border-slate-200">
      <tr className={open ? 'bg-slate-50' : 'hover:bg-slate-50'}>
        <th scope="row" className="px-3 py-3 text-left align-top font-normal sm:px-4 sm:align-middle">
          <button
            type="button"
            onClick={() => setOpen((wasOpen) => !wasOpen)}
            aria-expanded={open}
            aria-controls={detailsId}
            aria-label={`${open ? 'Hide' : 'Show'} what the search on ${started} found`}
            className="flex items-center gap-2 text-left font-medium text-slate-800 hover:text-indigo-700"
          >
            <svg
              aria-hidden="true"
              viewBox="0 0 20 20"
              fill="currentColor"
              className={`h-4 w-4 shrink-0 text-slate-400 transition-transform ${open ? 'rotate-90' : ''}`}
            >
              <path
                fillRule="evenodd"
                d="M7.2 14.8a.75.75 0 0 1 0-1.06L10.94 10 7.2 6.26a.75.75 0 1 1 1.06-1.06l4.27 4.27a.75.75 0 0 1 0 1.06l-4.27 4.27a.75.75 0 0 1-1.06 0Z"
                clipRule="evenodd"
              />
            </svg>
            <span>
              <span className="block sm:whitespace-nowrap">{started}</span>
              {duration && <span className="block text-xs font-normal text-slate-500">Took {duration}</span>}
            </span>
          </button>
        </th>
        <td className="px-3 py-3 sm:px-4">
          <span
            className={`inline-block whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium ring-1 ${RUN_STATUS_CLASS[run.status]}`}
          >
            {RUN_STATUS_LABEL[run.status]}
          </span>
          {run.sourcesFailed > 0 && (
            <span className="mt-1 block whitespace-nowrap text-xs text-slate-500">
              {sourcesFailedText(run.sourcesFailed)}
            </span>
          )}
          <span className="mt-1 block text-xs text-slate-600 sm:hidden">{totalsText(run)}</span>
        </td>
        <td className={numberCell}>{run.companiesSearched}</td>
        <td className={`${numberCell} ${run.newJobs > 0 ? 'font-semibold text-emerald-700' : ''}`}>{run.newJobs}</td>
        <td className={numberCell}>{run.duplicateJobs}</td>
        <td className={numberCell}>{run.filteredOutJobs}</td>
      </tr>
      {open && (
        <tr id={detailsId} className="bg-slate-50">
          <td colSpan={columns} className="px-2 pb-4 pt-1 sm:px-4">
            <RunDetails run={run} />
          </td>
        </tr>
      )}
    </tbody>
  )
}
