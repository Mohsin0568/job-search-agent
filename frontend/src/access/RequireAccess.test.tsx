import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { useProfile } from '../api/hooks'
import type { AccessStatus } from '../api/types'
import { renderRoutes } from '../test/render'
import { API, server } from '../test/server'
import { RequireAccess } from './RequireAccess'

const routes = [{ element: <RequireAccess />, children: [{ path: '/', element: <p>The app</p> }] }]

const access = (status: AccessStatus) =>
  http.get(`${API}/api/access`, () => HttpResponse.json({ status, inviteOnly: true }))

/** A page whose own request is turned away, as when access is taken away mid-session. */
function PageThatLoadsTheProfile() {
  const { isError } = useProfile()
  return <p>{isError ? 'Profile failed' : 'The app'}</p>
}

describe('RequireAccess', () => {
  it('shows the app to a user who has been let in', async () => {
    server.use(access('APPROVED'))
    renderRoutes(routes)

    expect(screen.queryByText('The app')).not.toBeInTheDocument()
    expect(await screen.findByText('The app')).toBeInTheDocument()
  })

  it('shows a user without an invite that they are on the waitlist, with their email', async () => {
    server.use(access('PENDING'))
    renderRoutes(routes)

    expect(await screen.findByRole('heading', { name: 'You’re on the waitlist' })).toBeInTheDocument()
    expect(screen.getByText('ada@example.com')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sign out' })).toBeInTheDocument()
    expect(screen.queryByText('The app')).not.toBeInTheDocument()
  })

  it('lets a waitlisted user in once they have been invited, when they check again', async () => {
    let status: AccessStatus = 'PENDING'
    server.use(http.get(`${API}/api/access`, () => HttpResponse.json({ status, inviteOnly: true })))
    renderRoutes(routes)
    await screen.findByRole('heading', { name: 'You’re on the waitlist' })

    status = 'APPROVED'
    await userEvent.click(screen.getByRole('button', { name: 'Check again' }))

    expect(await screen.findByText('The app')).toBeInTheDocument()
  })

  it('tells a user whose access was taken away, without offering to check again', async () => {
    server.use(access('REVOKED'))
    renderRoutes(routes)

    expect(await screen.findByRole('heading', { name: 'Your access has been removed' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Check again' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sign out' })).toBeInTheDocument()
  })

  it('signs the user out from the waitlist', async () => {
    server.use(access('PENDING'))
    const { auth } = renderRoutes(routes)

    await userEvent.click(await screen.findByRole('button', { name: 'Sign out' }))

    expect(auth.signOut).toHaveBeenCalledOnce()
  })

  it('shows the waitlist as soon as any request says the user is no longer let in', async () => {
    let status: AccessStatus = 'APPROVED'
    server.use(
      http.get(`${API}/api/access`, () => HttpResponse.json({ status, inviteOnly: true })),
      http.get(`${API}/api/profile`, () => {
        status = 'REVOKED'
        return HttpResponse.json({ message: 'removed', code: 'ACCESS_REVOKED' }, { status: 403 })
      }),
    )
    renderRoutes([{ element: <RequireAccess />, children: [{ path: '/', element: <PageThatLoadsTheProfile /> }] }])

    expect(await screen.findByRole('heading', { name: 'Your access has been removed' })).toBeInTheDocument()
  })

  it('offers a retry when access cannot be checked, rather than guessing', async () => {
    let fail = true
    server.use(
      http.get(`${API}/api/access`, () =>
        fail
          ? HttpResponse.json({ message: 'Cognito is down' }, { status: 503 })
          : HttpResponse.json({ status: 'APPROVED', inviteOnly: true }),
      ),
    )
    renderRoutes(routes)

    expect(await screen.findByText('We couldn’t check your account')).toBeInTheDocument()
    expect(screen.getByText('Cognito is down')).toBeInTheDocument()

    fail = false
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByText('The app')).toBeInTheDocument()
  })
})
