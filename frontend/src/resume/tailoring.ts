import type { ResumeTemplate, TailoredResume } from '../api/types'

/** Every template, in the order to offer them. */
export const RESUME_TEMPLATES: { id: ResumeTemplate; label: string }[] = [
  { id: 'CLASSIC', label: 'Classic' },
  { id: 'MODERN', label: 'Modern' },
  { id: 'COMPACT', label: 'Compact' },
  { id: 'EVEN', label: 'Even' },
  { id: 'STACK_OVERFLOW', label: 'Stack Overflow' },
  { id: 'KENDALL', label: 'Kendall' },
]

export function templateLabel(template: ResumeTemplate): string {
  return RESUME_TEMPLATES.find(({ id }) => id === template)?.label ?? template
}

/** The template the user asked for most recently, to offer first on a job they haven't tailored to yet. */
export function preferredTemplate(tailoredResumes: TailoredResume[]): ResumeTemplate {
  const latest = tailoredResumes.reduce<TailoredResume | undefined>(
    (latest, tailored) => (!latest || Date.parse(tailored.requestedAt) > Date.parse(latest.requestedAt) ? tailored : latest),
    undefined,
  )
  return latest?.template ?? 'CLASSIC'
}

/** How often to ask the server whether a resume being tailored is done. */
export const TAILORING_POLL_INTERVAL_MS = 3000

// The server gives up on a request left pending this long (TailoredResumeService.PENDING_TIMEOUT -
// keep in sync) and lets it be started again.
export const TAILORING_TIMEOUT_MS = 10 * 60 * 1000

export type TailoringState =
  /** The user has no resume to tailor. */
  | 'noResume'
  /** Never asked for. */
  | 'none'
  | 'tailoring'
  /** Pending for so long that the server has almost certainly lost it. */
  | 'stalled'
  | 'ready'
  /** Ready, but tailored from a resume the user has since replaced. */
  | 'outdated'
  | 'failed'

function pendingFor(tailored: TailoredResume, now: number): number {
  const requestedAt = new Date(tailored.requestedAt).getTime()
  return Number.isNaN(requestedAt) ? 0 : now - requestedAt
}

/** Whether the server is (still plausibly) working on it - which is when it is worth polling. */
export function isTailoring(tailored: TailoredResume, now = Date.now()): boolean {
  return tailored.status === 'PENDING' && pendingFor(tailored, now) < TAILORING_TIMEOUT_MS
}

export function tailoringState(
  tailored: TailoredResume | undefined,
  latestResumeVersion: number | null,
  now = Date.now(),
): TailoringState {
  if (latestResumeVersion === null) return 'noResume'
  if (!tailored) return 'none'
  switch (tailored.status) {
    case 'PENDING':
      return isTailoring(tailored, now) ? 'tailoring' : 'stalled'
    case 'FAILED':
      return 'failed'
    case 'READY':
      return tailored.resumeVersion === latestResumeVersion ? 'ready' : 'outdated'
  }
}
