import { zodResolver } from '@hookform/resolvers/zod'
import { autoSignIn, confirmSignUp, resendSignUpCode } from 'aws-amplify/auth'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { authErrorMessage } from '../auth/errors'
import type { AuthLocationState } from '../auth/routes'
import { confirmSignUpSchema, type ConfirmSignUpValues } from '../auth/schemas'
import { AuthLayout } from '../components/AuthLayout'
import { Alert } from '../components/form/Alert'
import { SubmitButton } from '../components/form/SubmitButton'
import { TextField } from '../components/form/TextField'

const RESEND_COOLDOWN_SECONDS = 30

export function ConfirmSignUpPage() {
  const navigate = useNavigate()
  const { refresh } = useAuth()
  const locationState = (useLocation().state ?? {}) as AuthLocationState
  const [formError, setFormError] = useState<string>()
  const [notice, setNotice] = useState(locationState.message)
  const [cooldown, setCooldown] = useState(0)

  const {
    register,
    handleSubmit,
    getValues,
    trigger,
    formState: { errors, isSubmitting },
  } = useForm<ConfirmSignUpValues>({
    resolver: zodResolver(confirmSignUpSchema),
    defaultValues: { email: locationState.email ?? '', code: '' },
  })

  useEffect(() => {
    if (cooldown <= 0) return
    const timer = setTimeout(() => setCooldown((s) => s - 1), 1000)
    return () => clearTimeout(timer)
  }, [cooldown])

  const onSubmit = async ({ email, code }: ConfirmSignUpValues) => {
    setFormError(undefined)
    try {
      const { nextStep } = await confirmSignUp({ username: email, confirmationCode: code })

      if (nextStep.signUpStep === 'COMPLETE_AUTO_SIGN_IN') {
        try {
          await autoSignIn()
          await refresh()
          navigate('/', { replace: true })
          return
        } catch {
          // Auto sign-in can fail (e.g. page was reloaded). Fall through to the login screen.
        }
      }
      const state: AuthLocationState = { email, message: 'Email verified. Sign in to continue.' }
      navigate('/login', { replace: true, state })
    } catch (error) {
      setFormError(authErrorMessage(error))
    }
  }

  const onResend = async () => {
    if (!(await trigger('email'))) return
    setFormError(undefined)
    setNotice(undefined)
    try {
      await resendSignUpCode({ username: getValues('email') })
      setNotice('We’ve sent a new code to your email.')
      setCooldown(RESEND_COOLDOWN_SECONDS)
    } catch (error) {
      setFormError(authErrorMessage(error))
    }
  }

  return (
    <AuthLayout title="Verify your email" subtitle="Enter the 6-digit code we emailed you">
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
        {notice && !formError && <Alert variant="success">{notice}</Alert>}
        {formError && <Alert variant="error">{formError}</Alert>}

        {locationState.email ? (
          <p className="text-sm text-slate-600">
            Code sent to <span className="font-medium text-slate-900">{locationState.email}</span>
          </p>
        ) : (
          <TextField label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
        )}
        <TextField
          label="Verification code"
          inputMode="numeric"
          autoComplete="one-time-code"
          maxLength={6}
          autoFocus={!!locationState.email}
          error={errors.code?.message}
          {...register('code')}
        />

        <SubmitButton loading={isSubmitting}>Verify email</SubmitButton>
      </form>

      <div className="mt-6 flex items-center justify-between text-sm">
        <button
          type="button"
          onClick={onResend}
          disabled={cooldown > 0}
          className="font-medium text-indigo-600 hover:underline disabled:cursor-not-allowed disabled:text-slate-400 disabled:no-underline"
        >
          {cooldown > 0 ? `Resend code in ${cooldown}s` : 'Resend code'}
        </button>
        <Link to="/login" className="text-slate-600 hover:underline">
          Back to sign in
        </Link>
      </div>
    </AuthLayout>
  )
}
