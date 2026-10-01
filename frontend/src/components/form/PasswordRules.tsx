import { PASSWORD_RULES } from '../../auth/schemas'

export function PasswordRules({ password }: { password: string }) {
  return (
    <ul className="grid grid-cols-1 gap-1 text-xs sm:grid-cols-2" aria-label="Password requirements">
      {PASSWORD_RULES.map((rule) => {
        const met = rule.test(password)
        return (
          <li key={rule.label} className={met ? 'text-emerald-600' : 'text-slate-500'}>
            <span aria-hidden="true">{met ? '✓' : '○'}</span> {rule.label}
            <span className="sr-only">{met ? ' (met)' : ' (not met)'}</span>
          </li>
        )
      })}
    </ul>
  )
}
