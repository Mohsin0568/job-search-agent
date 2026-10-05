import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext'

/** Router state passed between the auth screens. */
export type AuthLocationState = {
  email?: string
  message?: string
  /** How to style `message`; defaults to success. */
  messageTone?: 'success' | 'info'
  /** Where to return after sign-in. */
  from?: string
}

export function FullPageSpinner() {
  return (
    <div className="flex min-h-screen items-center justify-center">
      <div className="h-8 w-8 animate-spin rounded-full border-4 border-indigo-200 border-t-indigo-600" />
    </div>
  )
}

/** Only signed-in users get through; everyone else goes to /login. */
export function ProtectedRoute() {
  const { status, sessionExpired } = useAuth()
  const location = useLocation()

  if (status === 'loading') return <FullPageSpinner />
  if (status === 'signedOut') {
    const state: AuthLocationState = {
      from: location.pathname + location.search,
      message: sessionExpired ? 'Your session has expired. Please sign in again.' : undefined,
      messageTone: 'info',
    }
    return <Navigate to="/login" replace state={state} />
  }
  return <Outlet />
}

/** Login/register screens: a user who is already signed in goes straight to the app. */
export function PublicOnlyRoute() {
  const { status } = useAuth()

  if (status === 'loading') return <FullPageSpinner />
  if (status === 'signedIn') return <Navigate to="/" replace />
  return <Outlet />
}
