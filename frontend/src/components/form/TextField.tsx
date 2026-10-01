import { useId, type ComponentProps } from 'react'
import { FieldMessage } from './FieldMessage'
import { describedBy, inputClass, labelClass } from './fieldStyles'

type Props = ComponentProps<'input'> & {
  label: string
  error?: string
  hint?: string
}

export function TextField({ label, error, hint, className, ...inputProps }: Props) {
  const id = useId()

  return (
    <div className={className}>
      <label htmlFor={id} className={labelClass}>
        {label}
      </label>
      <input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(id, error, hint)}
        className={inputClass(!!error)}
        {...inputProps}
      />
      <FieldMessage id={id} error={error} hint={hint} />
    </div>
  )
}
