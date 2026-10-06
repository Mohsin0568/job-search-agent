import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { autoSignIn, signUp } from 'aws-amplify/auth'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { namedError, renderRoutes } from '../test/render'
import { RegisterPage } from './RegisterPage'

vi.mock('aws-amplify/auth', () => ({ signUp: vi.fn(), autoSignIn: vi.fn() }))

const routes = [
  { path: '/register', element: <RegisterPage /> },
  { path: '*', element: <p>Another page</p> },
]

const PASSWORD = 'Str0ng!pass'

function nextStep(signUpStep: string) {
  return { isSignUpComplete: signUpStep === 'DONE', nextStep: { signUpStep } } as Awaited<ReturnType<typeof signUp>>
}

async function submit(password = PASSWORD, confirmPassword = password) {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('Full name'), 'Ada Lovelace')
  await user.type(screen.getByLabelText('Email'), 'Ada@Example.com')
  await user.type(screen.getByLabelText('Password'), password)
  await user.type(screen.getByLabelText('Confirm password'), confirmPassword)
  await user.click(screen.getByRole('button', { name: 'Create account' }))
}

beforeEach(() => {
  vi.mocked(signUp).mockReset()
  vi.mocked(autoSignIn).mockReset()
})

describe('RegisterPage', () => {
  it('registers the user and opens the verify screen for their email', async () => {
    vi.mocked(signUp).mockResolvedValue(nextStep('CONFIRM_SIGN_UP'))
    const { router } = renderRoutes(routes, { at: '/register' })

    await submit()

    await waitFor(() => expect(router.state.location.pathname).toBe('/confirm'))
    expect(router.state.location.state).toEqual({ email: 'ada@example.com' })
    expect(signUp).toHaveBeenCalledWith({
      username: 'ada@example.com',
      password: PASSWORD,
      options: { userAttributes: { email: 'ada@example.com', name: 'Ada Lovelace' }, autoSignIn: true },
    })
  })

  it('signs the user straight in when the pool needs no email verification', async () => {
    vi.mocked(signUp).mockResolvedValue(nextStep('COMPLETE_AUTO_SIGN_IN'))
    vi.mocked(autoSignIn).mockResolvedValue({} as Awaited<ReturnType<typeof autoSignIn>>)
    const { router, auth } = renderRoutes(routes, { at: '/register' })

    await submit()

    await waitFor(() => expect(router.state.location.pathname).toBe('/'))
    expect(autoSignIn).toHaveBeenCalledOnce()
    expect(auth.refresh).toHaveBeenCalledOnce()
  })

  it('sends the user to sign in when registration is already complete', async () => {
    vi.mocked(signUp).mockResolvedValue(nextStep('DONE'))
    const { router } = renderRoutes(routes, { at: '/register' })

    await submit()

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    expect(router.state.location.state).toEqual({
      email: 'ada@example.com',
      message: 'Account created. You can sign in now.',
    })
  })

  it('ticks off the password requirements as they are met', async () => {
    renderRoutes(routes, { at: '/register' })
    const rules = within(screen.getByRole('list', { name: 'Password requirements' }))

    expect(rules.getByText(/At least 8 characters/)).toHaveTextContent('(not met)')

    await userEvent.setup().type(screen.getByLabelText('Password'), 'longenough')

    expect(rules.getByText(/At least 8 characters/)).toHaveTextContent('(met)')
    expect(rules.getByText(/An uppercase letter/)).toHaveTextContent('(not met)')
  })

  it('does not register with a weak password', async () => {
    renderRoutes(routes, { at: '/register' })

    await submit('weak')

    expect(await screen.findByText("Password doesn't meet all the requirements")).toBeInTheDocument()
    expect(signUp).not.toHaveBeenCalled()
  })

  it('does not register when the passwords differ', async () => {
    renderRoutes(routes, { at: '/register' })

    await submit(PASSWORD, `${PASSWORD}typo`)

    expect(await screen.findByText("Passwords don't match")).toBeInTheDocument()
    expect(signUp).not.toHaveBeenCalled()
  })

  it('says when the email is already registered', async () => {
    vi.mocked(signUp).mockRejectedValue(namedError('UsernameExistsException'))
    const { router } = renderRoutes(routes, { at: '/register' })

    await submit()

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'An account with this email already exists. Try signing in instead.',
    )
    expect(router.state.location.pathname).toBe('/register')
  })
})
