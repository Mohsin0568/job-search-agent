import { useEffect } from 'react'
import { Outlet } from 'react-router-dom'
import { setAccessDeniedHandler } from '../api/client'
import { useAccess } from '../api/hooks'
import { FullPageSpinner } from '../auth/routes'
import { WaitlistPage } from './WaitlistPage'

/**
 * While the app is invite-only, only users who have been let in get through; anyone else sees the
 * waitlist. If any request later says the user isn't let in - their access was taken away - it shows then.
 */
export function RequireAccess() {
  const { data: access, isPending, isError, error, refetch, isFetching } = useAccess()

  useEffect(() => {
    setAccessDeniedHandler(() => void refetch())
    return () => setAccessDeniedHandler(undefined)
  }, [refetch])

  if (isPending) return <FullPageSpinner />
  if (isError) {
    return (
      <div className="flex min-h-screen items-center justify-center px-4">
        <div className="w-full max-w-md rounded-xl border border-red-200 bg-red-50 p-6 text-center">
          <h1 className="font-semibold text-red-800">We couldn’t check your account</h1>
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
      </div>
    )
  }
  if (access.status !== 'APPROVED') {
    return <WaitlistPage status={access.status} onCheckAgain={() => void refetch()} checking={isFetching} />
  }
  return <Outlet />
}
