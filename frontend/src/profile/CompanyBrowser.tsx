import { useEffect, useId, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { useKnownCompanies } from '../api/hooks'
import type { KnownCompany } from '../api/types'
import {
  filterCompanies,
  groupBySector,
  initiallyTicked,
  OTHER_SECTOR,
  withTicked,
  type CompanySort,
} from './companyBrowsing'

const buttonClass =
  'rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 shadow-sm transition hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-indigo-200 disabled:cursor-not-allowed disabled:opacity-60'
const primaryButtonClass =
  'rounded-md bg-indigo-600 px-3 py-1.5 text-sm font-semibold text-white shadow-sm transition hover:bg-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-300 disabled:cursor-not-allowed disabled:opacity-60'

const SORTS: { value: CompanySort; label: string }[] = [
  { value: 'name', label: 'A–Z' },
  { value: 'sector', label: 'By sector' },
]

type Props = {
  /** The companies in the field now. */
  current: string[]
  /** The most companies a profile can search. */
  max: number
  /** With the field's new list of companies. */
  onDone: (companies: string[]) => void
  /** Without changing anything. */
  onCancel: () => void
}

/** Every known company, A-Z or by sector, to tick the ones to search rather than know their names. */
export function CompanyBrowser({ current, max, onDone, onCancel }: Props) {
  const companies = useKnownCompanies()
  const headingId = useId()
  const filterId = useId()
  const filterInput = useRef<HTMLInputElement>(null)
  const [query, setQuery] = useState('')
  const [sort, setSort] = useState<CompanySort>('name')
  // Known companies ticked, by their listed names; undefined until the list has loaded.
  const [ticked, setTicked] = useState<string[]>()

  const known = companies.data
  const tickedNow = ticked ?? (known ? initiallyTicked(current, known) : [])
  const result = known ? withTicked(current, known, tickedNow) : current
  const atLimit = result.length >= max

  useEffect(() => {
    filterInput.current?.focus()
  }, [])

  useEffect(() => {
    const cancelOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onCancel()
    }
    document.addEventListener('keydown', cancelOnEscape)
    return () => document.removeEventListener('keydown', cancelOnEscape)
  }, [onCancel])

  const toggle = (name: string) =>
    setTicked(tickedNow.includes(name) ? tickedNow.filter((each) => each !== name) : [...tickedNow, name])

  const checkbox = (company: KnownCompany, showSector: boolean) => {
    const isTicked = tickedNow.includes(company.name)
    return (
      <li key={company.name}>
        <label
          className={`flex items-baseline gap-2 rounded px-2 py-1.5 text-sm ${
            !isTicked && atLimit ? 'text-slate-400' : 'cursor-pointer text-slate-800 hover:bg-slate-50'
          }`}
        >
          <input
            type="checkbox"
            checked={isTicked}
            disabled={!isTicked && atLimit}
            onChange={() => toggle(company.name)}
            className="h-4 w-4 shrink-0 translate-y-0.5 rounded border-slate-300 text-indigo-600 focus:ring-indigo-300"
          />
          <span className="min-w-0">
            {company.name}
            {showSector && <span className="block text-xs text-slate-500">{company.sector ?? OTHER_SECTOR}</span>}
          </span>
        </label>
      </li>
    )
  }

  const listClass = 'grid gap-x-4 sm:grid-cols-2 lg:grid-cols-3'

  const list = () => {
    if (companies.isPending) {
      return (
        <p role="status" className="flex items-center gap-2 py-8 text-sm text-slate-600">
          <span className="h-4 w-4 animate-spin rounded-full border-2 border-indigo-200 border-t-indigo-600" />
          Loading companies…
        </p>
      )
    }
    if (companies.isError) {
      return (
        <div className="py-8 text-sm">
          <p className="text-red-700">We couldn’t load the list of companies.</p>
          <button
            type="button"
            onClick={() => void companies.refetch()}
            disabled={companies.isFetching}
            className={`${buttonClass} mt-3`}
          >
            {companies.isFetching ? 'Retrying…' : 'Try again'}
          </button>
        </div>
      )
    }
    const shown = filterCompanies(companies.data, query)
    if (shown.length === 0) {
      return (
        <p className="py-8 text-sm text-slate-600">
          No companies match “{query.trim()}”. If it isn’t listed, close this and type its name into the field.
        </p>
      )
    }
    if (sort === 'name') {
      return (
        <ul aria-label="Companies" className={listClass}>
          {shown.map((company) => checkbox(company, true))}
        </ul>
      )
    }
    return (
      <div className="space-y-5">
        {groupBySector(shown).map((group, index) => {
          const groupId = `${headingId}-sector-${index}`
          return (
            <section key={group.sector} aria-labelledby={groupId}>
              <h3 id={groupId} className="mb-1 flex items-baseline gap-2 text-sm font-semibold text-slate-900">
                {group.sector}
                <span className="text-xs font-normal text-slate-500">{group.companies.length}</span>
              </h3>
              <ul aria-label={group.sector} className={listClass}>
                {group.companies.map((company) => checkbox(company, false))}
              </ul>
            </section>
          )
        })}
      </div>
    )
  }

  // On top of the whole page, not inside the form that opened it.
  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4" onClick={onCancel}>
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={headingId}
        onClick={(event) => event.stopPropagation()}
        className="flex max-h-[90vh] w-full max-w-4xl flex-col rounded-lg bg-white shadow-xl"
      >
        <div className="border-b border-slate-200 p-5">
          <div className="flex items-start justify-between gap-4">
            <div>
              <h2 id={headingId} className="text-lg font-semibold text-slate-900">
                Browse companies
              </h2>
              <p className="mt-1 text-sm text-slate-500">Tick the companies whose jobs you want us to search for.</p>
            </div>
            <button
              type="button"
              onClick={onCancel}
              aria-label="Close"
              className="rounded-md px-2 text-2xl leading-none text-slate-400 hover:text-slate-600 focus:outline-none focus:ring-2 focus:ring-indigo-200"
            >
              ×
            </button>
          </div>
          <div className="mt-4 flex flex-wrap items-end gap-3">
            <div className="min-w-48 flex-1">
              <label htmlFor={filterId} className="block text-xs font-medium text-slate-600">
                Filter companies
              </label>
              <input
                id={filterId}
                ref={filterInput}
                type="search"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="e.g. bank"
                className="mt-1 block w-full rounded-md border border-slate-300 px-2.5 py-1.5 text-sm shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-200"
              />
            </div>
            <fieldset>
              <legend className="text-xs font-medium text-slate-600">Sort</legend>
              <div className="mt-1 inline-flex rounded-md border border-slate-300 p-0.5 shadow-sm">
                {SORTS.map((option) => (
                  <label
                    key={option.value}
                    className={`cursor-pointer rounded px-3 py-1 text-sm font-medium has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-indigo-200 ${
                      sort === option.value ? 'bg-indigo-600 text-white' : 'text-slate-700 hover:bg-slate-50'
                    }`}
                  >
                    <input
                      type="radio"
                      name={`${headingId}-sort`}
                      value={option.value}
                      checked={sort === option.value}
                      onChange={() => setSort(option.value)}
                      className="sr-only"
                    />
                    {option.label}
                  </label>
                ))}
              </div>
            </fieldset>
          </div>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto px-5 py-3">{list()}</div>

        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 p-4">
          <p className="text-sm text-slate-600" aria-live="polite">
            <span className="font-medium text-slate-800">
              {result.length} of {max}
            </span>{' '}
            selected
            {atLimit && <span className="text-slate-500"> · Untick one to choose another.</span>}
          </p>
          <div className="flex gap-2">
            <button type="button" onClick={onCancel} className={buttonClass}>
              Cancel
            </button>
            <button type="button" onClick={() => onDone(result)} disabled={!known} className={primaryButtonClass}>
              Done
            </button>
          </div>
        </div>
      </div>
    </div>,
    document.body,
  )
}
