import { useId, type ComponentProps } from 'react'
import { FieldMessage } from './FieldMessage'
import { describedBy, inputClass, labelClass } from './fieldStyles'

type Props = ComponentProps<'textarea'> & {
  label: string
  error?: string
  hint?: string
}

export function TextAreaField({ label, error, hint, className, ...textareaProps }: Props) {
  const id = useId()

  return (
    <div className={className}>
      <label htmlFor={id} className={labelClass}>
        {label}
      </label>
      <textarea
        id={id}
        rows={5}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(id, error, hint)}
        className={inputClass(!!error)}
        {...textareaProps}
      />
      <FieldMessage id={id} error={error} hint={hint} />
    </div>
  )
}
