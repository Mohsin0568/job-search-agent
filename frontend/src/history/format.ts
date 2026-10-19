import type { JobSearchRunStatus } from '../api/types'

export const RUN_STATUS_LABEL: Record<JobSearchRunStatus, string> = {
  SUCCEEDED: 'Completed',
  PARTIAL: 'Partly completed',
  FAILED: 'Failed',
  SKIPPED: 'Nothing to search',
}

export const RUN_STATUS_CLASS: Record<JobSearchRunStatus, string> = {
  SUCCEEDED: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  PARTIAL: 'bg-amber-50 text-amber-800 ring-amber-200',
  FAILED: 'bg-red-50 text-red-700 ring-red-200',
  SKIPPED: 'bg-slate-100 text-slate-600 ring-slate-200',
}

/** "18 Oct 2026, 02:00" in the user's own time zone, from an ISO instant. */
export function formatRunStart(instant: string): string {
  const date = new Date(instant)
  if (Number.isNaN(date.getTime())) return instant
  return date.toLocaleString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/** How long a run took: "45 sec", "5 min", "1 hr 5 min". Null when either time can't be read. */
export function formatDuration(startedAt: string, finishedAt: string): string | null {
  const millis = new Date(finishedAt).getTime() - new Date(startedAt).getTime()
  if (Number.isNaN(millis) || millis < 0) return null
  const seconds = Math.round(millis / 1000)
  if (seconds < 60) return `${seconds} sec`
  const minutes = Math.round(seconds / 60)
  if (minutes < 60) return `${minutes} min`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest === 0 ? `${hours} hr` : `${hours} hr ${rest} min`
}

/** "3 new · 1 already saved · 2 filtered out": a run's totals on one line, for a narrow screen. */
export function totalsText(totals: { newJobs: number; duplicateJobs: number; filteredOutJobs: number }): string {
  return `${totals.newJobs} new · ${totals.duplicateJobs} already saved · ${totals.filteredOutJobs} filtered out`
}

/** "1 source failed", "2 sources failed". */
export function sourcesFailedText(count: number): string {
  return `${count} ${count === 1 ? 'source' : 'sources'} failed`
}
