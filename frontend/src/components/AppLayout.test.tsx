import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { renderRoutes } from '../test/render'
import { AppLayout } from './AppLayout'

const routes = [
  {
    element: <AppLayout />,
    children: [
      { path: '/', element: <p>Dashboard content</p> },
      { path: '/onboarding', element: <p>Onboarding content</p> },
    ],
  },
  { path: '/login', element: <p>Sign-in page</p> },
]

describe('AppLayout', () => {
  it('shows the page with the navigation and the signed-in email', () => {
    renderRoutes(routes)

    expect(screen.getByText('Dashboard content')).toBeInTheDocument()
    expect(screen.getByText('ada@example.com')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Dashboard' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: 'Profile' })).not.toHaveAttribute('aria-current')
  })

  it('hides the navigation during onboarding, since the other pages only redirect back', () => {
    renderRoutes(routes, { at: '/onboarding' })

    expect(screen.getByText('Onboarding content')).toBeInTheDocument()
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sign out' })).toBeInTheDocument()
  })

  it('signs the user out and returns to the sign-in screen', async () => {
    const { router, auth } = renderRoutes(routes)

    await userEvent.setup().click(screen.getByRole('button', { name: 'Sign out' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    expect(auth.signOut).toHaveBeenCalledOnce()
  })
})
