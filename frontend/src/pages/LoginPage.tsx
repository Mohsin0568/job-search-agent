import { zodResolver } from '@hookform/resolvers/zod'
import { resendSignUpCode, signIn } from 'aws-amplify/auth'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { authErrorMessage } from '../auth/errors'
import type { AuthLocationState } from '../auth/routes'
import { loginSchema, type LoginValues } from '../auth/schemas'
import { AuthLayout } from '../components/AuthLayout'
import { Alert } from '../components/form/Alert'
import { SubmitButton } from '../components/form/SubmitButton'
import { TextField } from '../components/form/TextField'

export function LoginPage() {
  const navigate = useNavigate()
  const { refresh } = useAuth()
  const locationState = (useLocation().state ?? {}) as AuthLocationState
  const [formError, setFormError] = useState<string>()

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: locationState.email ?? '', password: '' },
  })

  const goToConfirm = async (email: string) => {
    // The user registered but never verified; send a fresh code so they aren't stuck.
    await resendSignUpCode({ username: email }).catch(() => undefined)
    const state: AuthLocationState = {
      email,
      message: 'Your email isn’t verified yet. We’ve sent you a new code.',
    }
    navigate('/confirm', { state })
  }

  const onSubmit = async ({ email, password }: LoginValues) => {
    setFormError(undefined)
    try {
      const { nextStep } = await signIn({ username: email, password })

      switch (nextStep.signInStep) {
        case 'DONE':
          await refresh()
          navigate(locationState.from ?? '/', { replace: true })
          return
        case 'CONFIRM_SIGN_UP':
          await goToConfirm(email)
          return
        case 'RESET_PASSWORD': {
          const state: AuthLocationState = {
            email,
            message: 'You need to reset your password before signing in.',
            messageTone: 'info',
          }
          navigate('/forgot-password', { state })
          return
        }
        default:
          // MFA / new-password challenges aren't enabled on the User Pool yet.
          setFormError(`This sign-in step isn't supported yet (${nextStep.signInStep}).`)
      }
    } catch (error) {
      if (error instanceof Error && error.name === 'UserNotConfirmedException') {
        await goToConfirm(email)
        return
      }
      setFormError(authErrorMessage(error))
    }
  }

  return (
    <AuthLayout title="Sign in" subtitle="Welcome back">
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
        {locationState.message && !formError && (
          <Alert variant={locationState.messageTone ?? 'success'}>{locationState.message}</Alert>
        )}
        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField
          label="Email"
          type="email"
          autoComplete="email"
          autoFocus={!locationState.email}
          error={errors.email?.message}
          {...register('email')}
        />
        <div>
          <TextField
            label="Password"
            type="password"
            autoComplete="current-password"
            autoFocus={!!locationState.email}
            error={errors.password?.message}
            {...register('password')}
          />
          <div className="mt-1 text-right">
            <Link to="/forgot-password" className="text-xs text-indigo-600 hover:underline">
              Forgot password?
            </Link>
          </div>
        </div>

        <SubmitButton loading={isSubmitting}>Sign in</SubmitButton>
      </form>

      <p className="mt-6 text-center text-sm text-slate-600">
        New here?{' '}
        <Link to="/register" className="font-medium text-indigo-600 hover:underline">
          Create an account
        </Link>
      </p>
    </AuthLayout>
  )
}
