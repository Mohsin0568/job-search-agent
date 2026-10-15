import { useId, useState } from 'react'
import type { AppliedFix, ResumeReview, TailoringFix } from '../api/types'

const primaryButtonClass =
  'rounded-md bg-indigo-600 px-3 py-1.5 text-sm font-semibold text-white shadow-sm transition hover:bg-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-300 disabled:cursor-not-allowed disabled:opacity-60'
const secondaryButtonClass =
  'rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 shadow-sm transition hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-indigo-200 disabled:cursor-not-allowed disabled:opacity-60'

type Props = {
  /** Names the job in the panel's accessible name, so a screen reader can tell the cards apart. */
  jobTitle: string
  /** A finished review of the user's latest resume that found something to fix. */
  review: ResumeReview
  /** What the job's tailored resume fixes now, to start from. Null when it was tailored as is, or never. */
  current: AppliedFix | null
  busy: boolean
  onTailor: (fix: TailoringFix) => void
  onCancel: () => void
}

/**
 * Chooses what tailoring fixes from the review: the missing keywords the user really has - none ticked
 * until they say so - and whether to address the red flags.
 */
export function TailorFixPanel({ jobTitle, review, current, busy, onTailor, onCancel }: Props) {
  const keywords = review.missingKeywords ?? []
  const redFlags = review.redFlags ?? []
  const [ticked, setTicked] = useState<Set<string>>(
    () => new Set(keywords.filter((keyword) => current?.keywords.includes(keyword))),
  )
  // Red flags are only ever reworded, never invented away, so addressing them is the starting choice.
  const [fixRedFlags, setFixRedFlags] = useState(current ? current.redFlags.length > 0 : redFlags.length > 0)
  const headingId = useId()

  const toggle = (keyword: string) =>
    setTicked((before) => {
      const after = new Set(before)
      if (after.has(keyword)) after.delete(keyword)
      else after.add(keyword)
      return after
    })

  // Unticking everything is a way back to tailoring as is.
  const nothingChosen = ticked.size === 0 && !fixRedFlags
  const submitLabel = nothingChosen ? 'Tailor as is' : 'Tailor with these fixes'

  return (
    <section aria-labelledby={headingId} className="space-y-3 rounded-md border border-slate-200 bg-slate-50 p-3">
      <h4 id={headingId} className="font-medium text-slate-800">
        <span className="sr-only">Fixes for {jobTitle}: </span>What to fix
      </h4>

      {keywords.length > 0 && (
        <fieldset>
          <legend className="text-xs font-medium text-slate-500">Missing keywords</legend>
          <p className="mt-1 text-xs text-slate-500">
            Tick only the ones you really have: they’ll be added to your resume.
          </p>
          <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1.5">
            {keywords.map((keyword) => (
              <label key={keyword} className="flex items-center gap-1.5 text-slate-700">
                <input
                  type="checkbox"
                  checked={ticked.has(keyword)}
                  onChange={() => toggle(keyword)}
                  className="h-4 w-4 rounded border-slate-300 text-indigo-600 focus:ring-indigo-200"
                />
                {keyword}
              </label>
            ))}
          </div>
        </fieldset>
      )}

      {redFlags.length > 0 && (
        <div>
          <label className="flex items-center gap-1.5 text-slate-700">
            <input
              type="checkbox"
              checked={fixRedFlags}
              onChange={(event) => setFixRedFlags(event.target.checked)}
              className="h-4 w-4 rounded border-slate-300 text-indigo-600 focus:ring-indigo-200"
            />
            Address the red flags
          </label>
          <ul className="mt-1 list-disc space-y-0.5 pl-6 text-xs text-slate-600">
            {redFlags.map((flag) => (
              <li key={flag.issue}>{flag.issue}</li>
            ))}
          </ul>
          <p className="mt-1 text-xs text-slate-500">
            By rewording what your resume already says. Some, like a missing degree, can’t be fixed honestly and are
            left alone.
          </p>
        </div>
      )}

      <div className="flex flex-wrap gap-2">
        <button
          type="button"
          onClick={() => onTailor({ keywords: keywords.filter((keyword) => ticked.has(keyword)), redFlags: fixRedFlags })}
          disabled={busy}
          aria-label={`${submitLabel} for ${jobTitle}`}
          className={primaryButtonClass}
        >
          {busy ? 'Starting…' : submitLabel}
        </button>
        <button type="button" onClick={onCancel} disabled={busy} className={secondaryButtonClass}>
          Cancel
        </button>
      </div>
    </section>
  )
}
