import { useCallback, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import type { ResumeTemplate } from '../api/types'
import { TailorFixPanel } from './TailorFixPanel'
import { fixSummary, RESUME_TEMPLATES, templateLabel } from './tailoring'
import { sentence, useTailoring, type Tailoring, type TailoringInput } from './useTailoring'
import { TemplateGallery } from './TemplateGallery'

const primaryButtonClass =
  'rounded-md bg-indigo-600 px-3 py-1.5 text-sm font-semibold text-white shadow-sm transition hover:bg-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-300 disabled:cursor-not-allowed disabled:opacity-60'
const secondaryButtonClass =
  'rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 shadow-sm transition hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-indigo-200 disabled:cursor-not-allowed disabled:opacity-60'

type Props = TailoringInput & {
  /** Names the job in each button's accessible name, so a screen reader can tell the cards apart. */
  jobTitle: string
}

/**
 * On a job card: tailor the user's resume to this job - as is, or fixing what the job's review found -
 * then download the result.
 */
export function TailoredResumeAction({ jobTitle, ...input }: Props) {
  const tailoring = useTailoring(input)
  return <TailoredResumeView tailoring={tailoring} jobTitle={jobTitle} />
}

type ViewProps = {
  tailoring: Tailoring
  jobTitle: string
  /**
   * When the card's menu offers "Tailor and fix" and "Tailor as is" (see tailoringMenuActions), they are
   * left off the card here.
   */
  fixActionsInMenu?: boolean
}

/** What TailoredResumeAction shows, for a card that runs the tailoring itself with useTailoring. */
export function TailoredResumeView({ tailoring, jobTitle, fixActionsInMenu = false }: ViewProps) {
  const { state, tailored, template, fixable, switching, requesting, choosingFixes, problem } = tailoring
  const [previewing, setPreviewing] = useState(false)
  const previewButton = useRef<HTMLButtonElement>(null)

  const tailorButton = (label: string, className: string) => (
    <button
      type="button"
      onClick={tailoring.tailor}
      disabled={requesting}
      aria-label={`${label} for ${jobTitle}`}
      className={className}
    >
      {requesting ? 'Starting…' : label}
    </button>
  )

  const tailorAsIsButton = (
    <button
      type="button"
      onClick={tailoring.tailorAsIs}
      disabled={requesting}
      aria-label={`Tailor as is for ${jobTitle}`}
      className={secondaryButtonClass}
    >
      {requesting ? 'Starting…' : 'Tailor as is'}
    </button>
  )

  const fixButton = (label: string, className: string) =>
    fixActionsInMenu ? null : (
      <button
        type="button"
        onClick={tailoring.openFixes}
        disabled={requesting || choosingFixes}
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
      onClick={() => void tailoring.download()}
      disabled={tailoring.downloading || switching}
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
    void tailoring.pick(wanted)
  }

  const picker = (
    <div className="flex items-center gap-2">
      <label className="flex items-center gap-2 text-xs text-slate-500">
        Template
        <select
          aria-label={`Resume template for ${jobTitle}`}
          value={template}
          disabled={requesting}
          onChange={(event) => void tailoring.pick(event.target.value as ResumeTemplate)}
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
        disabled={requesting}
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
          fixActionsInMenu ? (
            // While choosing, the fix panel below says it all.
            !choosingFixes && (
              <div className="space-y-2">
                <p className="text-xs text-slate-500">
                  {requesting ? 'Starting…' : 'Tailor your resume to this job from the ⋮ menu.'}
                </p>
                {picker}
              </div>
            )
          ) : (
            <div className="flex flex-wrap gap-2">
              {fixButton('Tailor and fix', primaryButtonClass)}
              {/* While choosing, the panel has its own way to tailor as is. */}
              {!choosingFixes && tailorAsIsButton}
              {picker}
            </div>
          )
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
          busy={requesting}
          onTailor={(fix) => void tailoring.tailorWithFixes(fix)}
          onCancel={tailoring.closeFixes}
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
