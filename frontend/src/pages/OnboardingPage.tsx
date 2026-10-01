import { Navigate, useNavigate } from 'react-router-dom'
import { useProfile } from '../api/hooks'
import { useAuth } from '../auth/AuthContext'
import { FullPageSpinner } from '../auth/routes'
import { ProfileForm } from '../profile/ProfileForm'

export function OnboardingPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const { data: profile, isPending } = useProfile()

  if (isPending) return <FullPageSpinner />
  // Already set up (e.g. navigated here directly): nothing to onboard.
  if (profile) return <Navigate to="/" replace />

  const firstName = user?.name?.split(' ')[0]

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="text-2xl font-semibold">Welcome{firstName ? `, ${firstName}` : ''}! Let’s set up your search</h1>
      <p className="mt-2 text-slate-500">
        Tell us what you’re looking for and which companies to watch. You can change this any time.
      </p>
      <div className="mt-8 rounded-xl border border-slate-200 bg-white p-6 shadow-sm sm:p-8">
        <ProfileForm submitLabel="Save and continue" onSaved={() => navigate('/', { replace: true })} />
      </div>
    </div>
  )
}
