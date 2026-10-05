import { Link } from 'react-router-dom'
import { useJobResults, useProfile } from '../api/hooks'
import { useAuth } from '../auth/AuthContext'
import { formatRunDateTime } from '../jobs/format'
import { companiesWithoutResults, groupByCompany } from '../jobs/groupByCompany'
import { JobCard } from '../jobs/JobCard'

// The newest page of results; older ones aren't shown on the dashboard.
const LATEST_RESULTS_LIMIT = 100

export function DashboardPage() {
  const { user } = useAuth()
  // RequireProfile has already loaded the profile, so this reads from the cache.
  const { data: profile } = useProfile()
  const results = useJobResults(0, LATEST_RESULTS_LIMIT)

  const preferred = profile?.companyPreferences ?? []
  const groups = groupByCompany(results.data ?? [], preferred)
  const missing = companiesWithoutResults(groups, preferred)
  // Results are sorted newest first, so the first one is the latest run.
  const lastUpdated = results.data?.[0]?.jobRunDateTime

  return (
    <div>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">Welcome{user?.name ? `, ${user.name}` : ''}</h1>
          <p className="mt-1 text-slate-500">
            Latest matches for <span className="font-medium text-slate-700">{profile?.desiredRole}</span>
            {lastUpdated && <> · Updated {formatRunDateTime(lastUpdated)}</>}
          </p>
        </div>
        <Link to="/profile" className="text-sm font-medium text-indigo-600 hover:underline">
          Edit search preferences
        </Link>
      </div>

      <div className="mt-8">
        {results.isPending ? (
          <ResultsSkeleton />
        ) : results.isError ? (
          <div className="rounded-xl border border-red-200 bg-red-50 p-6 text-center">
            <p className="font-medium text-red-800">We couldn’t load your job matches</p>
            <p className="mt-1 text-sm text-red-700">{results.error.message}</p>
            <button
              type="button"
              onClick={() => void results.refetch()}
              disabled={results.isFetching}
              className="mt-4 rounded-md border border-red-300 bg-white px-3 py-1.5 text-sm font-medium text-red-700 hover:bg-red-100 disabled:opacity-60"
            >
              {results.isFetching ? 'Retrying…' : 'Try again'}
            </button>
          </div>
        ) : groups.length === 0 ? (
          <div className="rounded-xl border border-dashed border-slate-300 bg-white p-10 text-center">
            <p className="font-medium text-slate-800">No job matches yet</p>
            <p className="mt-1 text-sm text-slate-500">
              We search your target companies every day. New matches will show up here after the next run.
            </p>
          </div>
        ) : (
          <div className="space-y-10">
            {groups.map((group, index) => (
              <section key={group.companyName} aria-labelledby={`company-${index}`}>
                <h2 id={`company-${index}`} className="flex items-baseline gap-2 text-lg font-semibold">
                  {group.companyName}
                  <span className="text-sm font-normal text-slate-500">
                    {group.results.length} {group.results.length === 1 ? 'job' : 'jobs'}
                  </span>
                </h2>
                <div className="mt-3 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                  {group.results.map((result) => (
                    <JobCard key={result.id} job={result.job} />
                  ))}
                </div>
              </section>
            ))}
            {missing.length > 0 && (
              <p className="text-sm text-slate-500">No recent openings found at {missing.join(', ')}.</p>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

function ResultsSkeleton() {
  return (
    <div className="space-y-3" aria-busy="true" aria-label="Loading job matches">
      <div className="h-6 w-40 animate-pulse rounded bg-slate-200" />
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {Array.from({ length: 3 }, (_, i) => (
          <div key={i} className="h-44 animate-pulse rounded-lg bg-slate-200" />
        ))}
      </div>
    </div>
  )
}
