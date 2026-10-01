type Props = {
  id: string
  error?: string
  hint?: string
}

/** The error (or, if none, the hint) under a field. Ids match the field's aria-describedby. */
export function FieldMessage({ id, error, hint }: Props) {
  if (error) {
    return (
      <p id={`${id}-error`} className="mt-1 text-xs text-red-600">
        {error}
      </p>
    )
  }
  if (hint) {
    return (
      <p id={`${id}-hint`} className="mt-1 text-xs text-slate-500">
        {hint}
      </p>
    )
  }
  return null
}

