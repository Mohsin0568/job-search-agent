import { useId, useState, type KeyboardEvent } from 'react'
import { FieldMessage } from './FieldMessage'
import { describedBy, labelClass } from './fieldStyles'

export type TagInputProps = {
  label: string
  value: string[]
  onChange: (value: string[]) => void
  onBlur?: () => void
  placeholder?: string
  maxItems: number
  maxItemLength: number
  error?: string
  hint?: string
  /** Optional autocomplete options for the current text; the user can still add their own. */
  suggestions?: string[]
  /** Called as the user types, e.g. to fetch `suggestions`. */
  onDraftChange?: (draft: string) => void
}

const MAX_VISIBLE_SUGGESTIONS = 8

// Loose comparison so "nestle", "Nestlé" and "NESTLE " are treated as the same entry.
function matchKey(value: string): string {
  return value
    .normalize('NFD')
    .replace(/\p{M}+/gu, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, ' ')
    .trim()
}

/**
 * A list of short strings: type and press Enter or comma to add, Backspace on an empty box to remove.
 * With `suggestions`, it's a combobox: arrow keys move through the list, Enter or a click picks one.
 */
export function TagInput({
  label,
  value,
  onChange,
  onBlur,
  placeholder,
  maxItems,
  maxItemLength,
  error,
  hint,
  suggestions = [],
  onDraftChange,
}: TagInputProps) {
  const id = useId()
  const listId = `${id}-suggestions`
  const [draft, setDraft] = useState('')
  const [open, setOpen] = useState(false)
  const [activeIndex, setActiveIndex] = useState<number | null>(null)
  const full = value.length >= maxItems

  const draftKey = matchKey(draft)
  const visible = draftKey
    ? suggestions
        .filter((suggestion) => !value.some((existing) => matchKey(existing) === matchKey(suggestion)))
        .slice(0, MAX_VISIBLE_SUGGESTIONS)
    : []
  const showList = open && !full && visible.length > 0
  // Pre-select the first suggestion when it completes what's typed ("delivero" -> Deliveroo), so Enter
  // fixes a half-typed or misspelt name; otherwise Enter adds the text as typed.
  const defaultIndex = visible.length > 0 && matchKey(visible[0]).startsWith(draftKey) ? 0 : null
  const highlighted = showList ? (activeIndex ?? defaultIndex) : null

  const updateDraft = (next: string) => {
    setDraft(next)
    setActiveIndex(null)
    setOpen(true)
    onDraftChange?.(next)
  }

  const add = (raw: string) => {
    updateDraft('')
    setOpen(false)
    const typed = raw.trim()
    if (!typed || full) return
    // Prefer the canonical spelling when the text matches a suggestion exactly.
    const item = suggestions.find((suggestion) => matchKey(suggestion) === matchKey(typed)) ?? typed
    if (value.some((existing) => matchKey(existing) === matchKey(item))) return
    onChange([...value, item])
  }

  const remove = (index: number) => onChange(value.filter((_, i) => i !== index))

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'ArrowDown' && visible.length > 0) {
      event.preventDefault()
      setOpen(true)
      setActiveIndex(highlighted === null ? 0 : Math.min(highlighted + 1, visible.length - 1))
    } else if (event.key === 'ArrowUp' && showList) {
      event.preventDefault()
      setActiveIndex(highlighted === null || highlighted === 0 ? null : highlighted - 1)
    } else if (event.key === 'Escape' && showList) {
      event.preventDefault()
      setOpen(false)
    } else if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault()
      add(highlighted !== null ? visible[highlighted] : draft)
    } else if (event.key === 'Backspace' && draft === '' && value.length > 0) {
      remove(value.length - 1)
    }
  }

  return (
    <div>
      <label htmlFor={id} className={labelClass}>
        {label}{' '}
        <span className="font-normal text-slate-400">
          ({value.length}/{maxItems})
        </span>
      </label>
      <div className="relative">
        <div
          className={`flex min-h-[42px] flex-wrap items-center gap-1.5 rounded-md border bg-white px-2 py-1.5 shadow-sm focus-within:ring-2 ${
            error
              ? 'border-red-400 focus-within:border-red-500 focus-within:ring-red-200'
              : 'border-slate-300 focus-within:border-indigo-500 focus-within:ring-indigo-200'
          }`}
        >
          {value.map((item, index) => (
            <span
              key={item}
              className="inline-flex max-w-full items-center gap-1 rounded-full bg-indigo-50 py-0.5 pl-2.5 pr-1 text-sm text-indigo-700"
            >
              <span className="truncate">{item}</span>
              <button
                type="button"
                onClick={() => remove(index)}
                aria-label={`Remove ${item}`}
                className="flex h-5 w-5 items-center justify-center rounded-full text-indigo-500 hover:bg-indigo-100 hover:text-indigo-700"
              >
                ×
              </button>
            </span>
          ))}
          <input
            id={id}
            value={draft}
            onChange={(event) => updateDraft(event.target.value)}
            onKeyDown={onKeyDown}
            onBlur={() => {
              add(draft)
              onBlur?.()
            }}
            disabled={full}
            maxLength={maxItemLength}
            placeholder={full ? 'Limit reached' : placeholder}
            autoComplete="off"
            role={onDraftChange ? 'combobox' : undefined}
            aria-autocomplete={onDraftChange ? 'list' : undefined}
            aria-expanded={onDraftChange ? showList : undefined}
            aria-controls={showList ? listId : undefined}
            aria-activedescendant={highlighted !== null ? `${listId}-${highlighted}` : undefined}
            aria-invalid={error ? true : undefined}
            aria-describedby={describedBy(id, error, hint)}
            className="min-w-[8rem] flex-1 border-0 bg-transparent px-1 py-1 text-sm outline-none disabled:cursor-not-allowed"
          />
        </div>

        {showList && (
          <ul
            id={listId}
            role="listbox"
            aria-label={`${label} suggestions`}
            className="absolute z-10 mt-1 max-h-64 w-full overflow-auto rounded-md border border-slate-200 bg-white py-1 text-sm shadow-lg"
          >
            {visible.map((suggestion, index) => (
              <li
                key={suggestion}
                id={`${listId}-${index}`}
                role="option"
                aria-selected={index === highlighted}
                // mousedown, not click: picking must happen before the input's blur adds the raw text.
                onMouseDown={(event) => {
                  event.preventDefault()
                  add(suggestion)
                }}
                onMouseEnter={() => setActiveIndex(index)}
                className={`cursor-pointer px-3 py-2 ${index === highlighted ? 'bg-indigo-50 text-indigo-700' : 'text-slate-700'}`}
              >
                {suggestion}
              </li>
            ))}
          </ul>
        )}
      </div>
      <FieldMessage id={id} error={error} hint={hint} />
    </div>
  )
}
