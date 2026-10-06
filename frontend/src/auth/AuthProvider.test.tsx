import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { fetchAuthSession, fetchUserAttributes, getCurrentUser, signOut } from 'aws-amplify/auth'
import { Hub } from 'aws-amplify/utils'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch } from '../api/client'
import { API, server } from '../test/server'
import { useAuth } from './AuthContext'
import { AuthProvider } from './AuthProvider'

vi.mock('aws-amplify/auth', () => ({
  fetchAuthSession: vi.fn(),
  fetchUserAttributes: vi.fn(),
  getCurrentUser: vi.fn(),
  signOut: vi.fn(),
}))

function Session() {
  const { status, user, sessionExpired, signOut } = useAuth()
  return (
    <>
      <p>
        {status}
        {user ? `: ${user.userId}, ${user.email}, ${user.name}` : ''}
        {sessionExpired ? ' (session expired)' : ''}
      </p>
      <button onClick={() => void signOut()}>Sign out</button>
    </>
  )
}

function renderProvider() {
  const queryClient = new QueryClient()
  queryClient.setQueryData(['profile', 'user-1'], { desiredRole: 'Java Developer' })
  render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <Session />
      </AuthProvider>
    </QueryClientProvider>,
  )
  return { cachedProfile: () => queryClient.getQueryData(['profile', 'user-1']) }
}

function signedIn() {
  vi.mocked(getCurrentUser).mockResolvedValue({
    userId: 'user-1',
    username: 'user-1',
    signInDetails: { loginId: 'login@example.com' },
  })
  vi.mocked(fetchUserAttributes).mockResolvedValue({ email: 'ada@example.com', name: 'Ada Lovelace' })
}

beforeEach(() => {
  vi.mocked(fetchAuthSession).mockReset().mockResolvedValue({})
  vi.mocked(fetchUserAttributes).mockReset()
  vi.mocked(getCurrentUser).mockReset()
  vi.mocked(signOut).mockReset().mockResolvedValue(undefined)
})

describe('AuthProvider', () => {
  it('is loading until the session has been read', async () => {
    signedIn()
    renderProvider()

    expect(screen.getByText('loading')).toBeInTheDocument()
    expect(await screen.findByText('signedIn: user-1, ada@example.com, Ada Lovelace')).toBeInTheDocument()
  })

  it('uses the sign-in email when the user has no email attribute', async () => {
    signedIn()
    vi.mocked(fetchUserAttributes).mockResolvedValue({})
    renderProvider()

    expect(await screen.findByText('signedIn: user-1, login@example.com, undefined')).toBeInTheDocument()
  })

  it('is signed out when there is no session', async () => {
    vi.mocked(getCurrentUser).mockRejectedValue(new Error('User needs to be authenticated'))
    renderProvider()

    expect(await screen.findByText('signedOut')).toBeInTheDocument()
  })

  it('signs out of Cognito and forgets the user’s cached data', async () => {
    signedIn()
    const { cachedProfile } = renderProvider()
    await screen.findByText(/^signedIn/)

    await userEvent.setup().click(screen.getByRole('button', { name: 'Sign out' }))

    expect(await screen.findByText('signedOut')).toBeInTheDocument()
    expect(signOut).toHaveBeenCalledOnce()
    expect(cachedProfile()).toBeUndefined()
  })

  it('ends the session as expired when the API rejects the token', async () => {
    signedIn()
    server.use(http.get(`${API}/api/profile`, () => new HttpResponse(null, { status: 401 })))
    const { cachedProfile } = renderProvider()
    await screen.findByText(/^signedIn/)

    await act(() => apiFetch('/api/profile').catch(() => undefined))

    expect(await screen.findByText('signedOut (session expired)')).toBeInTheDocument()
    expect(signOut).toHaveBeenCalledOnce()
    expect(cachedProfile()).toBeUndefined()
  })

  it('ends the session as expired when the token can no longer be refreshed', async () => {
    signedIn()
    renderProvider()
    await screen.findByText(/^signedIn/)

    act(() => Hub.dispatch('auth', { event: 'tokenRefresh_failure' }))

    expect(await screen.findByText('signedOut (session expired)')).toBeInTheDocument()
  })

  it('follows a sign-out and a sign-in made elsewhere', async () => {
    signedIn()
    renderProvider()
    await screen.findByText(/^signedIn/)

    act(() => Hub.dispatch('auth', { event: 'signedOut' }))
    expect(await screen.findByText('signedOut')).toBeInTheDocument()

    act(() => Hub.dispatch('auth', { event: 'signedIn', data: { userId: 'user-1', username: 'user-1' } }))
    expect(await screen.findByText(/^signedIn: user-1/)).toBeInTheDocument()
  })

  it('clears the expired notice once the user signs in again', async () => {
    signedIn()
    renderProvider()
    await screen.findByText(/^signedIn/)
    act(() => Hub.dispatch('auth', { event: 'tokenRefresh_failure' }))
    await screen.findByText('signedOut (session expired)')

    act(() => Hub.dispatch('auth', { event: 'signedIn', data: { userId: 'user-1', username: 'user-1' } }))

    await waitFor(() => expect(screen.getByText(/^signedIn: user-1/)).not.toHaveTextContent('session expired'))
  })
})
