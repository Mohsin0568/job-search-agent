import { useEffect, useId, useRef } from 'react'
import { createPortal } from 'react-dom'
import { templatePreviewUrl } from '../api/endpoints'
import type { ResumeTemplate } from '../api/types'
import { RESUME_TEMPLATES } from './tailoring'

type Props = {
  /** The template chosen now, shown as selected. */
  selected: ResumeTemplate
  onChoose: (template: ResumeTemplate) => void
  onClose: () => void
}

/** Every template laid out with a sample resume, side by side, to choose one by how it looks. */
export function TemplateGallery({ selected, onChoose, onClose }: Props) {
  const headingId = useId()
  const selectedButton = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    selectedButton.current?.focus()
  }, [])

  useEffect(() => {
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', closeOnEscape)
    return () => document.removeEventListener('keydown', closeOnEscape)
  }, [onClose])

  // On top of the whole page, not inside the job card that opened it.
  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4" onClick={onClose}>
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={headingId}
        onClick={(event) => event.stopPropagation()}
        className="max-h-[90vh] w-full max-w-5xl overflow-y-auto rounded-lg bg-white p-6 shadow-xl"
      >
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 id={headingId} className="text-lg font-semibold text-slate-900">
              Choose a template
            </h2>
            <p className="mt-1 text-sm text-slate-500">
              Shown with a sample resume. Yours is tailored to the job and laid out in the template you choose.
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="rounded-md px-2 text-2xl leading-none text-slate-400 hover:text-slate-600 focus:outline-none focus:ring-2 focus:ring-indigo-200"
          >
            ×
          </button>
        </div>

        <ul className="mt-5 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {RESUME_TEMPLATES.map(({ id, label }) => {
            const isSelected = id === selected
            return (
              <li key={id}>
                <button
                  type="button"
                  ref={isSelected ? selectedButton : undefined}
                  onClick={() => onChoose(id)}
                  aria-label={`${label} template`}
                  aria-pressed={isSelected}
                  className={`block w-full rounded-md p-2 text-left transition focus:outline-none focus:ring-2 focus:ring-indigo-300 ${
                    isSelected ? 'bg-indigo-50 ring-2 ring-indigo-500' : 'hover:bg-slate-50'
                  }`}
                >
                  <img
                    src={templatePreviewUrl(id)}
                    alt=""
                    loading="lazy"
                    className="aspect-[210/297] w-full border border-slate-200 bg-slate-100 shadow-sm"
                  />
                  <span className="mt-2 flex items-center justify-between text-sm font-medium text-slate-800">
                    {label}
                    {isSelected && <span className="text-xs font-semibold text-indigo-600">Selected</span>}
                  </span>
                </button>
              </li>
            )
          })}
        </ul>
      </div>
    </div>,
    document.body,
  )
}
