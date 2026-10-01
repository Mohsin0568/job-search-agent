import { Navigate, Outlet } from 'react-router-dom'
import { useProfile } from '../api/hooks'
import { FullPageSpinner } from '../auth/routes'

/** Users who haven't set up a profile yet are sent to onboarding - a job search can't run without one. */
export function RequireProfile() {
  const { data: profile, isPending, isError, error, refetch, isFetching } = useProfile()

  if (isPending) return <FullPageSpinner />
  if (isError) {
    return (
      <div className="mx-auto max-w-md rounded-xl border border-red-200 bg-red-50 p-6 text-center">
        <h1 className="font-semibold text-red-800">We couldn’t load your profile</h1>
        <p className="mt-1 text-sm text-red-700">{error.message}</p>
        <button
          type="button"
          onClick={() => void refetch()}
          disabled={isFetching}
          className="mt-4 rounded-md border border-red-300 bg-white px-3 py-1.5 text-sm font-medium text-red-700 hover:bg-red-100 disabled:opacity-60"
        >
          {isFetching ? 'Retrying…' : 'Try again'}
        </button>
      </div>
    )
  }
  if (profile === null) return <Navigate to="/onboarding" replace />
  return <Outlet />
}
