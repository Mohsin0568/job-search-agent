import { atsTone, type AtsTone } from './format'

const STYLES: Record<AtsTone, string> = {
  strong: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  fair: 'bg-amber-50 text-amber-700 ring-amber-200',
  weak: 'bg-slate-100 text-slate-600 ring-slate-200',
}

function ScoreBadge({ label, score, title }: { label: string; score: number; title: string }) {
  return (
    <span
      title={title}
      className={`shrink-0 whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-semibold ring-1 ring-inset ${STYLES[atsTone(score)]}`}
    >
      {label} {score}%
    </span>
  )
}

/** The search's score: how well the job fits the user's profile. */
export function AtsBadge({ score }: { score: number | null }) {
  if (score === null) {
    return <span className="shrink-0 text-xs text-slate-400">No match score</span>
  }
  return (
    <ScoreBadge
      label="Profile match"
      score={score}
      title="Profile match: how well this job fits your profile - role, skills and preferences (80+ is a strong match)"
    />
  )
}

/** The resume review's score: how an applicant tracking system would rate the user's resume for the job. */
export function ResumeAtsBadge({ score }: { score: number }) {
  return (
    <ScoreBadge
      label="Resume ATS"
      score={score}
      title="Resume ATS score: how an applicant tracking system would rate your resume for this job (80+ is strong)"
    />
  )
}
