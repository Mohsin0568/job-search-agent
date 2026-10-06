import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderRoutes } from '../test/render'
import { ProtectedRoute, PublicOnlyRoute } from './routes'

const SIGNED_OUT = { status: 'signedOut', user: null } as const
const LOADING = { status: 'loading', user: null } as const

const routes = [
  { element: <ProtectedRoute />, children: [{ path: '/profile', element: <p>Private page</p> }] },
  { element: <PublicOnlyRoute />, children: [{ path: '/login', element: <p>Sign-in page</p> }] },
  { path: '/', element: <p>Home page</p> },
]

describe('ProtectedRoute', () => {
  it('shows the page to a signed-in user', () => {
    renderRoutes(routes, { at: '/profile' })

    expect(screen.getByText('Private page')).toBeInTheDocument()
  })

  it('sends a signed-out user to sign in, remembering where they were going', () => {
    const { router } = renderRoutes(routes, { at: '/profile?tab=skills', auth: SIGNED_OUT })

    expect(screen.getByText('Sign-in page')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
    expect(router.state.location.state).toEqual({ from: '/profile?tab=skills', message: undefined, messageTone: 'info' })
  })

  it('explains that the session expired when it ended without the user signing out', () => {
    const { router } = renderRoutes(routes, { at: '/profile', auth: { ...SIGNED_OUT, sessionExpired: true } })

    expect(router.state.location.state).toMatchObject({
      message: 'Your session has expired. Please sign in again.',
      messageTone: 'info',
    })
  })

  it('shows neither the page nor the sign-in screen while the session is loading', () => {
    const { router } = renderRoutes(routes, { at: '/profile', auth: LOADING })

    expect(screen.queryByText('Private page')).not.toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/profile')
  })
})

describe('PublicOnlyRoute', () => {
  it('shows the sign-in screen to a signed-out user', () => {
    renderRoutes(routes, { at: '/login', auth: SIGNED_OUT })

    expect(screen.getByText('Sign-in page')).toBeInTheDocument()
  })

  it('sends a signed-in user to the app', () => {
    const { router } = renderRoutes(routes, { at: '/login' })

    expect(screen.getByText('Home page')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/')
  })

  it('waits while the session is loading', () => {
    const { router } = renderRoutes(routes, { at: '/login', auth: LOADING })

    expect(screen.queryByText('Sign-in page')).not.toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
  })
})
