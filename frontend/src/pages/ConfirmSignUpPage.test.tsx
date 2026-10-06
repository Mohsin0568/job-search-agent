import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { autoSignIn, confirmSignUp, resendSignUpCode } from 'aws-amplify/auth'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { namedError, renderRoutes } from '../test/render'
import { ConfirmSignUpPage } from './ConfirmSignUpPage'

vi.mock('aws-amplify/auth', () => ({ confirmSignUp: vi.fn(), autoSignIn: vi.fn(), resendSignUpCode: vi.fn() }))

const routes = [
  { path: '/confirm', element: <ConfirmSignUpPage /> },
  { path: '*', element: <p>Another page</p> },
]

// How the register screen opens this page.
const fromRegister = { pathname: '/confirm', state: { email: 'ada@example.com' } }

function nextStep(signUpStep: string) {
  return { isSignUpComplete: true, nextStep: { signUpStep } } as Awaited<ReturnType<typeof confirmSignUp>>
}

async function verify(code = '123456') {
  const user = userEvent.setup()
  if (code) await user.type(screen.getByLabelText('Verification code'), code)
  await user.click(screen.getByRole('button', { name: 'Verify email' }))
}

beforeEach(() => {
  vi.mocked(confirmSignUp).mockReset()
  vi.mocked(autoSignIn).mockReset()
  vi.mocked(resendSignUpCode).mockReset().mockResolvedValue({} as Awaited<ReturnType<typeof resendSignUpCode>>)
})

describe('ConfirmSignUpPage', () => {
  it('verifies the code and signs the user in automatically', async () => {
    vi.mocked(confirmSignUp).mockResolvedValue(nextStep('COMPLETE_AUTO_SIGN_IN'))
    vi.mocked(autoSignIn).mockResolvedValue({} as Awaited<ReturnType<typeof autoSignIn>>)
    const { router, auth } = renderRoutes(routes, { at: fromRegister })

    await verify()

    await waitFor(() => expect(router.state.location.pathname).toBe('/'))
    expect(confirmSignUp).toHaveBeenCalledWith({ username: 'ada@example.com', confirmationCode: '123456' })
    expect(autoSignIn).toHaveBeenCalledOnce()
    expect(auth.refresh).toHaveBeenCalledOnce()
  })

  it('falls back to the sign-in screen when automatic sign-in fails', async () => {
    vi.mocked(confirmSignUp).mockResolvedValue(nextStep('COMPLETE_AUTO_SIGN_IN'))
    vi.mocked(autoSignIn).mockRejectedValue(namedError('AutoSignInException'))
    const { router, auth } = renderRoutes(routes, { at: fromRegister })

    await verify()

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    expect(router.state.location.state).toEqual({
      email: 'ada@example.com',
      message: 'Email verified. Sign in to continue.',
    })
    expect(auth.refresh).not.toHaveBeenCalled()
  })

  it('goes to the sign-in screen when Cognito offers no automatic sign-in', async () => {
    vi.mocked(confirmSignUp).mockResolvedValue(nextStep('DONE'))
    const { router } = renderRoutes(routes, { at: fromRegister })

    await verify()

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    expect(autoSignIn).not.toHaveBeenCalled()
  })

  it('reports a wrong code', async () => {
    vi.mocked(confirmSignUp).mockRejectedValue(namedError('CodeMismatchException'))
    const { router } = renderRoutes(routes, { at: fromRegister })

    await verify()

    expect(await screen.findByRole('alert')).toHaveTextContent("That code isn't right. Check the email and try again.")
    expect(router.state.location.pathname).toBe('/confirm')
  })

  it('does not call Cognito with a code that is not six digits', async () => {
    renderRoutes(routes, { at: fromRegister })

    await verify('12')

    expect(await screen.findByText('Enter the 6-digit code')).toBeInTheDocument()
    expect(confirmSignUp).not.toHaveBeenCalled()
  })

  it('resends the code, then makes the user wait before resending again', async () => {
    renderRoutes(routes, { at: fromRegister })

    await userEvent.setup().click(screen.getByRole('button', { name: 'Resend code' }))

    expect(await screen.findByRole('status')).toHaveTextContent('We’ve sent a new code to your email.')
    expect(resendSignUpCode).toHaveBeenCalledWith({ username: 'ada@example.com' })
    expect(screen.getByRole('button', { name: /Resend code in \d+s/ })).toBeDisabled()
  })

  it('reports when the code cannot be resent', async () => {
    vi.mocked(resendSignUpCode).mockRejectedValue(namedError('LimitExceededException'))
    renderRoutes(routes, { at: fromRegister })

    await userEvent.setup().click(screen.getByRole('button', { name: 'Resend code' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Too many attempts. Wait a few minutes and try again.')
    expect(screen.getByRole('button', { name: 'Resend code' })).toBeEnabled()
  })

  it('asks for the email when opened directly, and needs it before resending', async () => {
    renderRoutes(routes, { at: '/confirm' })
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Resend code' }))

    expect(await screen.findByText('Enter a valid email address')).toBeInTheDocument()
    expect(resendSignUpCode).not.toHaveBeenCalled()

    await user.type(screen.getByLabelText('Email'), 'ada@example.com')
    await user.click(screen.getByRole('button', { name: 'Resend code' }))

    await waitFor(() => expect(resendSignUpCode).toHaveBeenCalledWith({ username: 'ada@example.com' }))
  })
})
