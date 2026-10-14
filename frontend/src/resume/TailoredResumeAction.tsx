import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useRequestTailoredResume, useTailoredResumeDownload } from '../api/hooks'
import type { TailoredResume } from '../api/types'
import { startDownload } from './download'
import { tailoringState } from './tailoring'

const primaryButtonClass =
  'rounded-md bg-indigo-600 px-3 py-1.5 text-sm font-semibold text-white shadow-sm transition hover:bg-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-300 disabled:cursor-not-allowed disabled:opacity-60'
const secondaryButtonClass =
  'rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 shadow-sm transition hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-indigo-200 disabled:cursor-not-allowed disabled:opacity-60'

// The server's reasons start in lower case, to follow "we couldn't…" in a sentence. Here they stand alone.
function sentence(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1)
}

type Props = {
  jobResultId: string
  /** Names the job in each button's accessible name, so a screen reader can tell the cards apart. */
  jobTitle: string
  /** The tailored resume for this job, if one was ever asked for. */
  tailored: TailoredResume | undefined
  /** The version of the user's current resume, or null if they haven't uploaded one. */
  latestResumeVersion: number | null
}

/** On a job card: tailor the user's resume to this job, then download the result. */
export function TailoredResumeAction({ jobResultId, jobTitle, tailored, latestResumeVersion }: Props) {
  const request = useRequestTailoredResume()
  const download = useTailoredResumeDownload()
  const [problem, setProblem] = useState<string>()

  const state = tailoringState(tailored, latestResumeVersion)

  const onTailor = async () => {
    setProblem(undefined)
    try {
      await request.mutateAsync(jobResultId)
    } catch (error) {
      setProblem(error instanceof Error ? sentence(error.message) : 'Couldn’t start tailoring. Please try again.')
    }
  }

  const onDownload = async () => {
    setProblem(undefined)
    try {
      const link = await download.mutateAsync(jobResultId)
      startDownload(link.url)
    } catch (error) {
      setProblem(error instanceof Error ? sentence(error.message) : 'Couldn’t download the resume. Please try again.')
    }
  }

  const tailorButton = (label: string, className: string) => (
    <button
      type="button"
      onClick={() => void onTailor()}
      disabled={request.isPending}
      aria-label={`${label} for ${jobTitle}`}
      className={className}
    >
      {request.isPending ? 'Starting…' : label}
    </button>
  )

  const downloadButton = (
    <button
      type="button"
      onClick={() => void onDownload()}
      disabled={download.isPending}
      aria-label={`Download tailored resume for ${jobTitle}`}
      className={primaryButtonClass}
    >
      Download tailored resume
    </button>
  )

  return (
    <div className="space-y-2">
      {state === 'noResume' && (
        <p className="text-slate-600">
          <Link to="/profile" className="font-medium text-indigo-600 hover:underline">
            Upload your resume
          </Link>{' '}
          to tailor it to this job.
        </p>
      )}

      {state === 'none' && tailorButton('Tailor resume', primaryButtonClass)}

      {state === 'tailoring' && (
        <p role="status" className="flex items-center gap-2 text-slate-600">
          <span className="h-4 w-4 animate-spin rounded-full border-2 border-indigo-200 border-t-indigo-600" />
          Tailoring your resume to this job…
        </p>
      )}

      {state === 'ready' && downloadButton}

      {state === 'outdated' && (
        <>
          <p className="text-xs text-slate-500">Tailored from an earlier version of your resume.</p>
          <div className="flex flex-wrap gap-2">
            {downloadButton}
            {tailorButton('Tailor again', secondaryButtonClass)}
          </div>
        </>
      )}

      {state === 'failed' && (
        <>
          <p className="text-red-700">
            {sentence(tailored?.failureReason ?? 'something went wrong while tailoring your resume')}.
          </p>
          {tailorButton('Try again', secondaryButtonClass)}
        </>
      )}

      {state === 'stalled' && (
        <>
          <p className="text-slate-600">Tailoring is taking longer than it should.</p>
          {tailorButton('Try again', secondaryButtonClass)}
        </>
      )}

      {problem && (
        <p role="alert" className="text-red-700">
          {problem}
        </p>
      )}
    </div>
  )
}
