export const labelClass = 'mb-1 block text-sm font-medium text-slate-700'

export function inputClass(hasError: boolean): string {
  return `block w-full rounded-md border px-3 py-2 text-sm shadow-sm outline-none transition focus:ring-2 disabled:bg-slate-100 ${
    hasError
      ? 'border-red-400 focus:border-red-500 focus:ring-red-200'
      : 'border-slate-300 focus:border-indigo-500 focus:ring-indigo-200'
  }`
}

/** Points a field's aria-describedby at the message FieldMessage renders. */
export function describedBy(id: string, error?: string, hint?: string): string | undefined {
  return error ? `${id}-error` : hint ? `${id}-hint` : undefined
}
