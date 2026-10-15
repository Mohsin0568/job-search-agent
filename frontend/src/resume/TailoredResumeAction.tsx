import { useCallback, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useRequestTailoredResume, useTailoredResumeDownload } from '../api/hooks'
import type { ResumeReview, ResumeTemplate, TailoredResume, TailoringFix } from '../api/types'
import { startDownload } from './download'
import { TailorFixPanel } from './TailorFixPanel'
import { fixableReview, fixSummary, RESUME_TEMPLATES, tailoringState, templateLabel } from './tailoring'
import { TemplateGallery } from './TemplateGallery'

const AS_IS: TailoringFix = { keywords: [], redFlags: false }

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
  /** The template to offer when this job has none yet. */
  defaultTemplate: ResumeTemplate
  /** The review of the user's resume for this job, whose findings tailoring can fix. */
  review?: ResumeReview
}

/**
 * On a job card: tailor the user's resume to this job - as is, or fixing what the job's review found -
 * then download the result.
 */
export function TailoredResumeAction({
  jobResultId,
  jobTitle,
  tailored,
  latestResumeVersion,
  defaultTemplate,
  review,
}: Props) {
  const request = useRequestTailoredResume()
  const download = useTailoredResumeDownload()
  const [problem, setProblem] = useState<string>()
  // The template picked here, until the server's record catches up with it.
  const [picked, setPicked] = useState<ResumeTemplate>()
  const [previewing, setPreviewing] = useState(false)
  const [choosingFixes, setChoosingFixes] = useState(false)
  const previewButton = useRef<HTMLButtonElement>(null)

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

  const onTailor = () => void tailorIn(template)

  const onTailorWithFixes = async (fix: TailoringFix) => {
    if (await tailorIn(template, fix)) setChoosingFixes(false)
  }

  const onPick = async (wanted: ResumeTemplate) => {
    if (wanted === template) return
    setPicked(wanted)
    if (state === 'ready' && !(await tailorIn(wanted))) {
      setPicked(undefined)
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
      onClick={onTailor}
      disabled={request.isPending}
      aria-label={`${label} for ${jobTitle}`}
      className={className}
    >
      {request.isPending ? 'Starting…' : label}
    </button>
  )

  const tailorAsIsButton = (
    <button
      type="button"
      onClick={() => void tailorIn(template, AS_IS)}
      disabled={request.isPending}
      aria-label={`Tailor as is for ${jobTitle}`}
      className={secondaryButtonClass}
    >
      {request.isPending ? 'Starting…' : 'Tailor as is'}
    </button>
  )

  const fixButton = (label: string, className: string) => (
    <button
      type="button"
      onClick={() => setChoosingFixes(true)}
      disabled={request.isPending || choosingFixes}
      aria-expanded={choosingFixes}
      aria-label={`${label} for ${jobTitle}`}
      className={className}
    >
      {label}
    </button>
  )

  const fixesApplied = tailored?.status === 'READY' ? fixSummary(tailored.fix) : null

  const downloadButton = (
    <button
      type="button"
      onClick={() => void onDownload()}
      disabled={download.isPending || switching}
      aria-label={`Download tailored resume for ${jobTitle}`}
      className={primaryButtonClass}
    >
      Download tailored resume
    </button>
  )

  const closePreview = useCallback(() => {
    setPreviewing(false)
    previewButton.current?.focus()
  }, [])

  const onChooseInPreview = (wanted: ResumeTemplate) => {
    closePreview()
    void onPick(wanted)
  }

  const picker = (
    <div className="flex items-center gap-2">
      <label className="flex items-center gap-2 text-xs text-slate-500">
        Template
        <select
          aria-label={`Resume template for ${jobTitle}`}
          value={template}
          disabled={request.isPending}
          onChange={(event) => void onPick(event.target.value as ResumeTemplate)}
          className="rounded-md border border-slate-300 bg-white py-1.5 pl-2 pr-8 text-sm text-slate-700 shadow-sm focus:outline-none focus:ring-2 focus:ring-indigo-200 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {RESUME_TEMPLATES.map(({ id, label }) => (
            <option key={id} value={id}>
              {label}
            </option>
          ))}
        </select>
      </label>
      <button
        type="button"
        ref={previewButton}
        onClick={() => setPreviewing(true)}
        disabled={request.isPending}
        aria-label={`Preview templates for ${jobTitle}`}
        className="text-sm font-medium text-indigo-600 hover:underline focus:outline-none focus:ring-2 focus:ring-indigo-200 disabled:cursor-not-allowed disabled:opacity-60"
      >
        Preview
      </button>
      {previewing && <TemplateGallery selected={template} onChoose={onChooseInPreview} onClose={closePreview} />}
    </div>
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

      {state === 'none' &&
        (fixable ? (
          <div className="flex flex-wrap gap-2">
            {fixButton('Tailor and fix', primaryButtonClass)}
            {/* While choosing, the panel has its own way to tailor as is. */}
            {!choosingFixes && tailorAsIsButton}
            {picker}
          </div>
        ) : (
          <div className="flex flex-wrap gap-2">
            {tailorButton('Tailor resume', primaryButtonClass)}
            {picker}
          </div>
        ))}

      {state === 'tailoring' && (
        <p role="status" className="flex items-center gap-2 text-slate-600">
          <span className="h-4 w-4 animate-spin rounded-full border-2 border-indigo-200 border-t-indigo-600" />
          Tailoring your resume to this job…
        </p>
      )}

      {state === 'ready' && (
        <>
          {fixesApplied && <p className="text-xs text-slate-500">{fixesApplied}</p>}
          <div className="flex flex-wrap gap-2">
            {downloadButton}
            {fixable && fixButton(tailored?.fix ? 'Change fixes' : 'Tailor and fix', secondaryButtonClass)}
            {picker}
          </div>
          {switching && (
            <p role="status" className="text-xs text-slate-500">
              Laying it out in {templateLabel(template)}…
            </p>
          )}
        </>
      )}

      {state === 'outdated' && (
        <>
          <p className="text-xs text-slate-500">Tailored from an earlier version of your resume.</p>
          <div className="flex flex-wrap gap-2">
            {downloadButton}
            {tailorButton('Tailor again', secondaryButtonClass)}
            {fixable && fixButton('Tailor and fix', secondaryButtonClass)}
            {picker}
          </div>
        </>
      )}

      {state === 'failed' && (
        <>
          <p className="text-red-700">
            {sentence(tailored?.failureReason ?? 'something went wrong while tailoring your resume')}.
          </p>
          <div className="flex flex-wrap gap-2">
            {tailorButton('Try again', secondaryButtonClass)}
            {picker}
          </div>
        </>
      )}

      {state === 'stalled' && (
        <>
          <p className="text-slate-600">Tailoring is taking longer than it should.</p>
          <div className="flex flex-wrap gap-2">
            {tailorButton('Try again', secondaryButtonClass)}
            {picker}
          </div>
        </>
      )}

      {choosingFixes && fixable && (state === 'none' || state === 'ready' || state === 'outdated') && (
        <TailorFixPanel
          jobTitle={jobTitle}
          review={fixable}
          current={state === 'ready' ? (tailored?.fix ?? null) : null}
          busy={request.isPending}
          onTailor={(fix) => void onTailorWithFixes(fix)}
          onCancel={() => setChoosingFixes(false)}
        />
      )}

      {problem && (
        <p role="alert" className="text-red-700">
          {problem}
        </p>
      )}
    </div>
  )
}
