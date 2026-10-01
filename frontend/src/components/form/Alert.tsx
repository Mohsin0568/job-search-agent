import type { ReactNode } from 'react'

type Props = {
  variant: 'error' | 'success'
  children: ReactNode
}

const STYLES = {
  error: 'border-red-200 bg-red-50 text-red-800',
  success: 'border-emerald-200 bg-emerald-50 text-emerald-800',
}

export function Alert({ variant, children }: Props) {
  return (
    <div role={variant === 'error' ? 'alert' : 'status'} className={`rounded-md border p-3 text-sm ${STYLES[variant]}`}>
      {children}
    </div>
  )
}
