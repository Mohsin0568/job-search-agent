import { useState } from 'react'
import { useRequestTailoredResume, useTailoredResumeDownload } from '../api/hooks'
import type { ResumeReview, ResumeTemplate, TailoredResume, TailoringFix } from '../api/types'
import type { MenuAction } from '../components/ActionMenu'
import { startDownload } from './download'
import { fixableReview, tailoringState } from './tailoring'

const AS_IS: TailoringFix = { keywords: [], redFlags: false }

// The server's reasons start in lower case, to follow "we couldn't…" in a sentence. Here they stand alone.
export function sentence(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1)
}

export type TailoringInput = {
  jobResultId: string
  /** The tailored resume for this job, if one was ever asked for. */
  tailored: TailoredResume | undefined
  /** The version of the user's current resume, or null if they haven't uploaded one. */
  latestResumeVersion: number | null
  /** The template to offer when this job has none yet. */
  defaultTemplate: ResumeTemplate
  /** The review of the user's resume for this job, whose findings tailoring can fix. */
  review?: ResumeReview
}

/** Tailoring one job's resume: where it's up to, and everything the user can do about it. */
export function useTailoring({ jobResultId, tailored, latestResumeVersion, defaultTemplate, review }: TailoringInput) {
  const request = useRequestTailoredResume()
  const download = useTailoredResumeDownload()
  const [problem, setProblem] = useState<string>()
  // The template picked here, until the server's record catches up with it.
  const [picked, setPicked] = useState<ResumeTemplate>()
  const [choosingFixes, setChoosingFixes] = useState(false)

  const state = tailoringState(tailored, latestResumeVersion)
  const template = picked ?? tailored?.template ?? defaultTemplate
  const fixable = fixableReview(review, latestResumeVersion)
  // A ready resume changes template straight away: the server lays the same content out again.
  const switching = state === 'ready' && request.isPending && request.variables?.fix === undefined

  // Without a fix the server keeps what the job's resume fixes now.
  const tailorIn = async (wanted: ResumeTemplate, fix?: TailoringFix) => {
    setProblem(undefined)
    try {
      await request.mutateAsync({ jobResultId, template: wanted, fix })
      return true
    } catch (error) {
      setProblem(error instanceof Error ? sentence(error.message) : 'Couldn’t start tailoring. Please try again.')
      return false
    }
  }

  return {
    tailored,
    state,
    template,
    fixable,
    switching,
    problem,
    choosingFixes,
    requesting: request.isPending,
    downloading: download.isPending,
    tailor: () => void tailorIn(template),
    tailorAsIs: () => void tailorIn(template, AS_IS),
    openFixes: () => setChoosingFixes(true),
    closeFixes: () => setChoosingFixes(false),
    tailorWithFixes: async (fix: TailoringFix) => {
      if (await tailorIn(template, fix)) setChoosingFixes(false)
    },
    pick: async (wanted: ResumeTemplate) => {
      if (wanted === template) return
      setPicked(wanted)
      if (state === 'ready' && !(await tailorIn(wanted))) {
        setPicked(undefined)
      }
    },
    download: async () => {
      setProblem(undefined)
      try {
        const link = await download.mutateAsync(jobResultId)
        startDownload(link.url)
      } catch (error) {
        setProblem(error instanceof Error ? sentence(error.message) : 'Couldn’t download the resume. Please try again.')
      }
    },
  }
}

export type Tailoring = ReturnType<typeof useTailoring>

/**
 * The tailoring actions that can sit in a card's menu rather than on the card: tailoring with fixes
 * from the review, and tailoring as is instead. Empty when the review found nothing to fix.
 */
export function tailoringMenuActions(tailoring: Tailoring, jobTitle: string): MenuAction[] {
  const { state, fixable, tailored, requesting, choosingFixes } = tailoring
  if (!fixable) return []
  const busy = requesting || choosingFixes

  const fix = (label: string): MenuAction => ({
    label,
    accessibleLabel: `${label} for ${jobTitle}`,
    onSelect: tailoring.openFixes,
    disabled: busy,
  })

  if (state === 'none') {
    return [
      fix('Tailor and fix'),
      { label: 'Tailor as is', accessibleLabel: `Tailor as is for ${jobTitle}`, onSelect: tailoring.tailorAsIs, disabled: busy },
    ]
  }
  if (state === 'ready') return [fix(tailored?.fix ? 'Change fixes' : 'Tailor and fix')]
  if (state === 'outdated') return [fix('Tailor and fix')]
  return []
}

