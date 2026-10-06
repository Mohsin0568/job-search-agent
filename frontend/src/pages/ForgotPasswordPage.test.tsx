import { screen, waitFor } from '@testing-library/react'
import userEvent, { type UserEvent } from '@testing-library/user-event'
import { confirmResetPassword, resetPassword } from 'aws-amplify/auth'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { namedError, renderRoutes } from '../test/render'
import { ForgotPasswordPage } from './ForgotPasswordPage'

vi.mock('aws-amplify/auth', () => ({ resetPassword: vi.fn(), confirmResetPassword: vi.fn() }))

const routes = [
  { path: '/forgot-password', element: <ForgotPasswordPage /> },
  { path: '*', element: <p>Another page</p> },
]

const NEW_PASSWORD = 'N3w!password'

async function requestCode(user: UserEvent, email = 'ada@example.com') {
  await user.type(screen.getByLabelText('Email'), email)
  await user.click(screen.getByRole('button', { name: 'Send reset code' }))
}

async function chooseNewPassword(user: UserEvent, { code = '123456', confirmPassword = NEW_PASSWORD } = {}) {
  await user.type(await screen.findByLabelText('Reset code'), code)
  await user.type(screen.getByLabelText('New password'), NEW_PASSWORD)
  await user.type(screen.getByLabelText('Confirm new password'), confirmPassword)
  await user.click(screen.getByRole('button', { name: 'Update password' }))
}

beforeEach(() => {
  vi.mocked(resetPassword).mockReset().mockResolvedValue({} as Awaited<ReturnType<typeof resetPassword>>)
  vi.mocked(confirmResetPassword).mockReset().mockResolvedValue(undefined)
})

describe('ForgotPasswordPage', () => {
  it('emails a code, sets the new password, then sends the user to sign in', async () => {
    const user = userEvent.setup()
    const { router } = renderRoutes(routes, { at: '/forgot-password' })

    await requestCode(user, 'Ada@Example.com')
    await chooseNewPassword(user)

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    expect(resetPassword).toHaveBeenCalledWith({ username: 'ada@example.com' })
    expect(confirmResetPassword).toHaveBeenCalledWith({
      username: 'ada@example.com',
      confirmationCode: '123456',
      newPassword: NEW_PASSWORD,
    })
    expect(router.state.location.state).toEqual({
      email: 'ada@example.com',
      message: 'Password updated. Sign in with your new password.',
    })
  })

  it('does not reveal that no account exists for the email', async () => {
    vi.mocked(resetPassword).mockRejectedValue(namedError('UserNotFoundException'))
    renderRoutes(routes, { at: '/forgot-password' })

    await requestCode(userEvent.setup(), 'nobody@example.com')

    expect(await screen.findByRole('heading', { name: 'Choose a new password' })).toBeInTheDocument()
    expect(screen.getByText("If an account exists for nobody@example.com, we've emailed it a code.")).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('reports other failures to send the code', async () => {
    vi.mocked(resetPassword).mockRejectedValue(namedError('LimitExceededException'))
    renderRoutes(routes, { at: '/forgot-password' })

    await requestCode(userEvent.setup())

    expect(await screen.findByRole('alert')).toHaveTextContent('Too many attempts. Wait a few minutes and try again.')
    expect(screen.getByRole('heading', { name: 'Reset your password' })).toBeInTheDocument()
  })

  it('shows why the user was sent here, with their email filled in', () => {
    renderRoutes(routes, {
      at: {
        pathname: '/forgot-password',
        state: { email: 'ada@example.com', message: 'You need to reset your password before signing in.', messageTone: 'info' },
      },
    })

    expect(screen.getByRole('status')).toHaveTextContent('You need to reset your password before signing in.')
    expect(screen.getByLabelText('Email')).toHaveValue('ada@example.com')
  })

  it('reports a wrong code and stays on the new-password step', async () => {
    vi.mocked(confirmResetPassword).mockRejectedValue(namedError('CodeMismatchException'))
    const user = userEvent.setup()
    const { router } = renderRoutes(routes, { at: '/forgot-password' })

    await requestCode(user)
    await chooseNewPassword(user)

    expect(await screen.findByRole('alert')).toHaveTextContent("That code isn't right. Check the email and try again.")
    expect(router.state.location.pathname).toBe('/forgot-password')
  })

  it('does not set the password when the confirmation differs', async () => {
    const user = userEvent.setup()
    renderRoutes(routes, { at: '/forgot-password' })

    await requestCode(user)
    await chooseNewPassword(user, { confirmPassword: 'Different1!' })

    expect(await screen.findByText("Passwords don't match")).toBeInTheDocument()
    expect(confirmResetPassword).not.toHaveBeenCalled()
  })

  it('lets the user start again with a different email', async () => {
    const user = userEvent.setup()
    renderRoutes(routes, { at: '/forgot-password' })

    await requestCode(user)
    await user.click(await screen.findByRole('button', { name: 'Use a different email' }))

    expect(screen.getByRole('heading', { name: 'Reset your password' })).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toHaveValue('')
  })
})
