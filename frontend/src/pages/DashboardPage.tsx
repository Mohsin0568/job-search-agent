import { useProfile } from '../api/hooks'
import { useAuth } from '../auth/AuthContext'

export function DashboardPage() {
  const { user } = useAuth()
  // RequireProfile has already loaded the profile, so this reads from the cache.
  const { data: profile } = useProfile()

  return (
    <div>
      <h1 className="text-2xl font-semibold">Welcome{user?.name ? `, ${user.name}` : ''}</h1>
      {profile && (
        <p className="mt-2 text-slate-500">
          Searching for <span className="font-medium text-slate-700">{profile.desiredRole}</span> roles at{' '}
          {profile.companyPreferences?.length ?? 0} companies.
        </p>
      )}
      {profile?.companyPreferences && profile.companyPreferences.length > 0 && (
        <ul className="mt-4 flex flex-wrap gap-2">
          {profile.companyPreferences.map((company) => (
            <li key={company} className="rounded-full bg-indigo-50 px-3 py-1 text-sm text-indigo-700">
              {company}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
