import { atsTone, type AtsTone } from './format'

const STYLES: Record<AtsTone, string> = {
  strong: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  fair: 'bg-amber-50 text-amber-700 ring-amber-200',
  weak: 'bg-slate-100 text-slate-600 ring-slate-200',
}

export function AtsBadge({ score }: { score: number | null }) {
  if (score === null) {
    return <span className="shrink-0 text-xs text-slate-400">No match score</span>
  }
  return (
    <span
      title="ATS match score: how well your profile matches this job (80+ is a strong match)"
      className={`shrink-0 rounded-full px-2.5 py-0.5 text-xs font-semibold ring-1 ring-inset ${STYLES[atsTone(score)]}`}
    >
      {score}% match
    </span>
  )
}
