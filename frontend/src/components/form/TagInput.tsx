import { useId, useState, type KeyboardEvent } from 'react'
import { FieldMessage } from './FieldMessage'
import { describedBy, labelClass } from './fieldStyles'

type Props = {
  label: string
  value: string[]
  onChange: (value: string[]) => void
  onBlur?: () => void
  placeholder?: string
  maxItems: number
  maxItemLength: number
  error?: string
  hint?: string
}

/** A list of short strings: type and press Enter or comma to add, Backspace on an empty box to remove. */
export function TagInput({ label, value, onChange, onBlur, placeholder, maxItems, maxItemLength, error, hint }: Props) {
  const id = useId()
  const [draft, setDraft] = useState('')
  const full = value.length >= maxItems

  const commitDraft = () => {
    const item = draft.trim()
    setDraft('')
    if (!item || full) return
    // Ignore duplicates, case-insensitively.
    if (value.some((existing) => existing.toLowerCase() === item.toLowerCase())) return
    onChange([...value, item])
  }

  const remove = (index: number) => onChange(value.filter((_, i) => i !== index))

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault()
      commitDraft()
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
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={onKeyDown}
          onBlur={() => {
            commitDraft()
            onBlur?.()
          }}
          disabled={full}
          maxLength={maxItemLength}
          placeholder={full ? 'Limit reached' : placeholder}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy(id, error, hint)}
          className="min-w-[8rem] flex-1 border-0 bg-transparent px-1 py-1 text-sm outline-none disabled:cursor-not-allowed"
        />
      </div>
      <FieldMessage id={id} error={error} hint={hint} />
    </div>
  )
}
