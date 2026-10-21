import { useState } from 'react'
import { useUpdateJobStatus } from '../api/hooks'
import type { JobSearchResult, JobStatus } from '../api/types'
import type { MenuAction } from '../components/ActionMenu'

/** Moving one job between the dashboard, Applied and Not relevant. */
export function useJobStatus(
  result: JobSearchResult,
  /** Called once the job has a new status (not after only its reason changed), with the job as it was. */
  onMoved?: (previous: JobSearchResult, updated: JobSearchResult) => void,
) {
  const update = useUpdateJobStatus()
  const [editingReason, setEditingReason] = useState(false)
  const [problem, setProblem] = useState<string>()

  const mark = async (status: JobStatus, reason?: string) => {
    setProblem(undefined)
    try {
      const updated = await update.mutateAsync({ jobResultId: result.id, status, reason })
      setEditingReason(false)
      if (updated.status !== result.status) onMoved?.(result, updated)
    } catch {
      setProblem('Couldn’t update this job. Please try again.')
    }
  }

  return {
    result,
    mark,
    saving: update.isPending,
    problem,
    editingReason,
    openReason: () => {
      setProblem(undefined)
      setEditingReason(true)
    },
    closeReason: () => setEditingReason(false),
  }
}

export type JobStatusControl = ReturnType<typeof useJobStatus>

/** The status changes a job can take, as items for its card's menu. */
export function jobStatusMenuActions(status: JobStatusControl, jobTitle: string): MenuAction[] {
  const current = status.result.status
  const disabled = status.saving
  const actions: MenuAction[] = []
  if (current !== 'APPLIED') {
    actions.push({
      label: 'Applied',
      accessibleLabel: `Mark ${jobTitle} as applied`,
      onSelect: () => void status.mark('APPLIED'),
      disabled,
    })
  }
  actions.push(
    current === 'NOT_RELEVANT'
      ? { label: 'Edit reason', accessibleLabel: `Edit why ${jobTitle} isn’t relevant`, onSelect: status.openReason, disabled }
      : { label: 'Not relevant', accessibleLabel: `Mark ${jobTitle} as not relevant`, onSelect: status.openReason, disabled },
  )
  if (current !== 'ACTIVE') {
    actions.push({
      label: 'Back to dashboard',
      accessibleLabel: `Move ${jobTitle} back to your dashboard`,
      onSelect: () => void status.mark('ACTIVE'),
      disabled,
    })
  }
  return actions
}
