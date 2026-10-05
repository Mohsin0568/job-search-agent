import { useState } from 'react'
import { Link, NavLink, Outlet, useMatch, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

const NAV_ITEMS = [
  { to: '/', label: 'Dashboard', end: true },
  { to: '/profile', label: 'Profile', end: false },
]

export function AppLayout() {
  const { user, signOut } = useAuth()
  const navigate = useNavigate()
  const [signingOut, setSigningOut] = useState(false)
  // Until onboarding is done the other pages just redirect back, so don't offer them.
  const onboarding = useMatch('/onboarding') !== null

  const handleSignOut = async () => {
    setSigningOut(true)
    try {
      await signOut()
      navigate('/login', { replace: true })
    } finally {
      setSigningOut(false)
    }
  }

  return (
    <div className="min-h-screen">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-5xl items-center justify-between gap-4 px-4 py-3">
          <div className="flex min-w-0 items-center gap-6">
            <Link to="/" className="shrink-0 font-semibold text-indigo-600">
              Job Search Agent
            </Link>
            {!onboarding && (
              <nav aria-label="Main" className="flex items-center gap-1">
                {NAV_ITEMS.map((item) => (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    end={item.end}
                    className={({ isActive }) =>
                      `rounded-md px-3 py-1.5 text-sm font-medium transition ${
                        isActive ? 'bg-indigo-50 text-indigo-700' : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
                      }`
                    }
                  >
                    {item.label}
                  </NavLink>
                ))}
              </nav>
            )}
          </div>
          <div className="flex min-w-0 items-center gap-4">
            <span className="hidden truncate text-sm text-slate-600 md:inline">{user?.email}</span>
            <button
              type="button"
              onClick={handleSignOut}
              disabled={signingOut}
              className="shrink-0 rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 transition hover:bg-slate-100 disabled:opacity-60"
            >
              {signingOut ? 'Signing out…' : 'Sign out'}
            </button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-5xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  )
}
