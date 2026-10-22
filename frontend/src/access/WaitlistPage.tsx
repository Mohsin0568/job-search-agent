import { useState } from 'react'
import type { AccessStatus } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { AuthLayout } from '../components/AuthLayout'

const buttonClass =
  'rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 shadow-sm transition hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-indigo-200 disabled:cursor-not-allowed disabled:opacity-60'
const primaryButtonClass =
  'rounded-md bg-indigo-600 px-3 py-1.5 text-sm font-semibold text-white shadow-sm transition hover:bg-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-300 disabled:cursor-not-allowed disabled:opacity-60'

type Props = {
  status: Exclude<AccessStatus, 'APPROVED'>
  /** Asks again whether the user has been let in. */
  onCheckAgain: () => void
  checking: boolean
}

/** What a signed-in user sees instead of the app while it is invite-only and they aren't let in. */
export function WaitlistPage({ status, onCheckAgain, checking }: Props) {
  const { user, signOut } = useAuth()
  const [signingOut, setSigningOut] = useState(false)

  const onSignOut = async () => {
    setSigningOut(true)
    try {
      await signOut()
    } finally {
      setSigningOut(false)
    }
  }

  const revoked = status === 'REVOKED'

  return (
    <AuthLayout
      title={revoked ? 'Your access has been removed' : 'You’re on the waitlist'}
      subtitle={revoked ? undefined : 'Job Search Agent is invite-only for now.'}
    >
      <div className="space-y-4 text-sm text-slate-600">
        {revoked ? (
          <p>
            You can no longer use Job Search Agent with{' '}
            <span className="font-medium text-slate-800">{user?.email}</span>.
          </p>
        ) : (
          <>
            <p>
              Your account is set up, and you’re on the list as{' '}
              <span className="font-medium text-slate-800">{user?.email}</span>.
            </p>
            <p>You’ll be able to set up your job search as soon as you’re invited. Check back later.</p>
          </>
        )}
        <div className="flex flex-wrap gap-2 pt-2">
          {!revoked && (
            <button type="button" onClick={onCheckAgain} disabled={checking} className={primaryButtonClass}>
              {checking ? 'Checking…' : 'Check again'}
            </button>
          )}
          <button type="button" onClick={() => void onSignOut()} disabled={signingOut} className={buttonClass}>
            {signingOut ? 'Signing out…' : 'Sign out'}
          </button>
        </div>
      </div>
    </AuthLayout>
  )
}
