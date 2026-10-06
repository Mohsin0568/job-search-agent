import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { OnboardingPage } from '../pages/OnboardingPage'
import { profile } from '../test/fixtures'
import { renderRoutes } from '../test/render'
import { API, server } from '../test/server'
import { RequireProfile } from './RequireProfile'

const routes = [
  { element: <RequireProfile />, children: [{ path: '/', element: <p>Dashboard</p> }] },
  { path: '/onboarding', element: <OnboardingPage /> },
]

const noProfile = () => http.get(`${API}/api/profile`, () => HttpResponse.json({ message: 'No profile' }, { status: 404 }))
const hasProfile = () => http.get(`${API}/api/profile`, () => HttpResponse.json(profile()))

describe('RequireProfile', () => {
  it('shows the page once the profile has loaded', async () => {
    server.use(hasProfile())
    renderRoutes(routes)

    expect(screen.queryByText('Dashboard')).not.toBeInTheDocument()
    expect(await screen.findByText('Dashboard')).toBeInTheDocument()
  })

  it('sends a user without a profile to onboarding', async () => {
    server.use(noProfile(), http.get(`${API}/api/:source/suggest`, () => HttpResponse.json([])))
    const { router } = renderRoutes(routes)

    await waitFor(() => expect(router.state.location.pathname).toBe('/onboarding'))
    expect(await screen.findByRole('heading', { name: 'Welcome, Ada! Let’s set up your search' })).toBeInTheDocument()
  })

  it('offers a retry when the profile cannot be loaded, rather than assuming there is none', async () => {
    server.use(
      http.get(`${API}/api/profile`, () => HttpResponse.json({ message: 'Database unavailable' }, { status: 503 })),
    )
    const { router } = renderRoutes(routes)

    expect(await screen.findByRole('heading', { name: 'We couldn’t load your profile' })).toBeInTheDocument()
    expect(screen.getByText('Database unavailable')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/')

    server.use(hasProfile())
    await userEvent.setup().click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByText('Dashboard')).toBeInTheDocument()
  })
})

describe('OnboardingPage', () => {
  it('sends a user who already has a profile to the dashboard', async () => {
    server.use(hasProfile())
    const { router } = renderRoutes(routes, { at: '/onboarding' })

    await waitFor(() => expect(router.state.location.pathname).toBe('/'))
    expect(await screen.findByText('Dashboard')).toBeInTheDocument()
  })
})
