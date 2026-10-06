import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { resendSignUpCode, signIn } from 'aws-amplify/auth'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { namedError, renderRoutes } from '../test/render'
import { LoginPage } from './LoginPage'

vi.mock('aws-amplify/auth', () => ({ signIn: vi.fn(), resendSignUpCode: vi.fn() }))

const routes = [
  { path: '/login', element: <LoginPage /> },
  { path: '*', element: <p>Another page</p> },
]

function nextStep(signInStep: string) {
  return { isSignedIn: signInStep === 'DONE', nextStep: { signInStep } } as Awaited<ReturnType<typeof signIn>>
}

async function submit(email = 'ada@example.com', password = 'Str0ng!pass') {
  const user = userEvent.setup()
  if (email) await user.type(screen.getByLabelText('Email'), email)
  if (password) await user.type(screen.getByLabelText('Password'), password)
  await user.click(screen.getByRole('button', { name: 'Sign in' }))
}

beforeEach(() => {
  vi.mocked(signIn).mockReset()
  vi.mocked(resendSignUpCode).mockReset().mockResolvedValue({} as Awaited<ReturnType<typeof resendSignUpCode>>)
})

describe('LoginPage', () => {
  it('signs in, reloads the session, then goes to the dashboard', async () => {
    vi.mocked(signIn).mockResolvedValue(nextStep('DONE'))
    const { router, auth } = renderRoutes(routes, { at: '/login', auth: { status: 'signedOut', user: null } })

    await submit('Ada@Example.com')

    await waitFor(() => expect(router.state.location.pathname).toBe('/'))
    expect(signIn).toHaveBeenCalledWith({ username: 'ada@example.com', password: 'Str0ng!pass' })
    expect(auth.refresh).toHaveBeenCalledOnce()
  })

  it('returns to the page the user was sent to sign in from', async () => {
    vi.mocked(signIn).mockResolvedValue(nextStep('DONE'))
    const { router } = renderRoutes(routes, { at: { pathname: '/login', state: { from: '/profile?tab=skills' } } })

    await submit()

    await waitFor(() => expect(router.state.location.pathname).toBe('/profile'))
    expect(router.state.location.search).toBe('?tab=skills')
  })

  it('shows the message it was opened with, with the email filled in', () => {
    renderRoutes(routes, {
      at: { pathname: '/login', state: { email: 'ada@example.com', message: 'Email verified. Sign in to continue.' } },
    })

    expect(screen.getByRole('status')).toHaveTextContent('Email verified. Sign in to continue.')
    expect(screen.getByLabelText('Email')).toHaveValue('ada@example.com')
  })

  it('does not call Cognito until the form is valid', async () => {
    renderRoutes(routes, { at: '/login' })

    await submit('not-an-email', '')

    expect(await screen.findByText('Enter a valid email address')).toBeInTheDocument()
    expect(screen.getByText('Enter your password')).toBeInTheDocument()
    expect(signIn).not.toHaveBeenCalled()
  })

  it('reports a wrong password without leaving the page', async () => {
    vi.mocked(signIn).mockRejectedValue(namedError('NotAuthorizedException', 'Incorrect username or password.'))
    const { router, auth } = renderRoutes(routes, { at: '/login' })

    await submit()

    expect(await screen.findByRole('alert')).toHaveTextContent('Incorrect email or password.')
    expect(router.state.location.pathname).toBe('/login')
    expect(auth.refresh).not.toHaveBeenCalled()
  })

  it.each([
    ['Cognito asks for the sign-up to be confirmed', () => vi.mocked(signIn).mockResolvedValue(nextStep('CONFIRM_SIGN_UP'))],
    [
      'Cognito rejects the unconfirmed user',
      () => vi.mocked(signIn).mockRejectedValue(namedError('UserNotConfirmedException')),
    ],
  ])('sends a new code and opens the verify screen when %s', async (_case, arrange) => {
    arrange()
    const { router } = renderRoutes(routes, { at: '/login' })

    await submit()

    await waitFor(() => expect(router.state.location.pathname).toBe('/confirm'))
    expect(resendSignUpCode).toHaveBeenCalledWith({ username: 'ada@example.com' })
    expect(router.state.location.state).toEqual({
      email: 'ada@example.com',
      message: 'Your email isn’t verified yet. We’ve sent you a new code.',
    })
  })

  it('still opens the verify screen when the new code cannot be sent', async () => {
    vi.mocked(signIn).mockResolvedValue(nextStep('CONFIRM_SIGN_UP'))
    vi.mocked(resendSignUpCode).mockRejectedValue(namedError('LimitExceededException'))
    const { router } = renderRoutes(routes, { at: '/login' })

    await submit()

    await waitFor(() => expect(router.state.location.pathname).toBe('/confirm'))
  })

  it('opens the reset screen when Cognito requires a password reset', async () => {
    vi.mocked(signIn).mockResolvedValue(nextStep('RESET_PASSWORD'))
    const { router } = renderRoutes(routes, { at: '/login' })

    await submit()

    await waitFor(() => expect(router.state.location.pathname).toBe('/forgot-password'))
    expect(router.state.location.state).toEqual({
      email: 'ada@example.com',
      message: 'You need to reset your password before signing in.',
      messageTone: 'info',
    })
  })

  it('says so when Cognito asks for a step the app does not support', async () => {
    vi.mocked(signIn).mockResolvedValue(nextStep('CONFIRM_SIGN_IN_WITH_TOTP_CODE'))
    const { router, auth } = renderRoutes(routes, { at: '/login' })

    await submit()

    expect(await screen.findByRole('alert')).toHaveTextContent(
      "This sign-in step isn't supported yet (CONFIRM_SIGN_IN_WITH_TOTP_CODE).",
    )
    expect(router.state.location.pathname).toBe('/login')
    expect(auth.refresh).not.toHaveBeenCalled()
  })
})
