import { zodResolver } from '@hookform/resolvers/zod'
import { confirmResetPassword, resetPassword } from 'aws-amplify/auth'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { authErrorMessage } from '../auth/errors'
import type { AuthLocationState } from '../auth/routes'
import {
  forgotPasswordConfirmSchema,
  forgotPasswordRequestSchema,
  type ForgotPasswordConfirmValues,
  type ForgotPasswordRequestValues,
} from '../auth/schemas'
import { AuthLayout } from '../components/AuthLayout'
import { Alert } from '../components/form/Alert'
import { PasswordRules } from '../components/form/PasswordRules'
import { SubmitButton } from '../components/form/SubmitButton'
import { TextField } from '../components/form/TextField'

export function ForgotPasswordPage() {
  const locationState = (useLocation().state ?? {}) as AuthLocationState
  // Once a code has been sent we switch to step 2 for that email.
  const [email, setEmail] = useState<string>()

  return email ? (
    <ConfirmStep email={email} onRestart={() => setEmail(undefined)} />
  ) : (
    <RequestStep
      initialEmail={locationState.email}
      message={locationState.message}
      messageTone={locationState.messageTone}
      onCodeSent={setEmail}
    />
  )
}

type RequestStepProps = {
  initialEmail?: string
  message?: string
  messageTone?: AuthLocationState['messageTone']
  onCodeSent: (email: string) => void
}

function RequestStep({ initialEmail, message, messageTone, onCodeSent }: RequestStepProps) {
  const [formError, setFormError] = useState<string>()
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ForgotPasswordRequestValues>({
    resolver: zodResolver(forgotPasswordRequestSchema),
    defaultValues: { email: initialEmail ?? '' },
  })

  const onSubmit = async ({ email }: ForgotPasswordRequestValues) => {
    setFormError(undefined)
    try {
      await resetPassword({ username: email })
      onCodeSent(email)
    } catch (error) {
      // Don't reveal whether an account exists for this email.
      if (error instanceof Error && error.name === 'UserNotFoundException') {
        onCodeSent(email)
        return
      }
      setFormError(authErrorMessage(error))
    }
  }

  return (
    <AuthLayout title="Reset your password" subtitle="We'll email you a code to reset it">
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
        {message && !formError && <Alert variant={messageTone ?? 'success'}>{message}</Alert>}
        {formError && <Alert variant="error">{formError}</Alert>}
        <TextField label="Email" type="email" autoComplete="email" autoFocus error={errors.email?.message} {...register('email')} />
        <SubmitButton loading={isSubmitting}>Send reset code</SubmitButton>
      </form>
      <p className="mt-6 text-center text-sm">
        <Link to="/login" className="text-slate-600 hover:underline">
          Back to sign in
        </Link>
      </p>
    </AuthLayout>
  )
}

function ConfirmStep({ email, onRestart }: { email: string; onRestart: () => void }) {
  const navigate = useNavigate()
  const [formError, setFormError] = useState<string>()
  const {
    register,
    handleSubmit,
    control,
    formState: { errors, isSubmitting },
  } = useForm<ForgotPasswordConfirmValues>({
    resolver: zodResolver(forgotPasswordConfirmSchema),
    defaultValues: { code: '', password: '', confirmPassword: '' },
  })
  const password = useWatch({ control, name: 'password' })

  const onSubmit = async ({ code, password }: ForgotPasswordConfirmValues) => {
    setFormError(undefined)
    try {
      await confirmResetPassword({ username: email, confirmationCode: code, newPassword: password })
      const state: AuthLocationState = { email, message: 'Password updated. Sign in with your new password.' }
      navigate('/login', { replace: true, state })
    } catch (error) {
      setFormError(authErrorMessage(error))
    }
  }

  return (
    <AuthLayout title="Choose a new password" subtitle={`If an account exists for ${email}, we've emailed it a code.`}>
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
        {formError && <Alert variant="error">{formError}</Alert>}
        <TextField
          label="Reset code"
          inputMode="numeric"
          autoComplete="one-time-code"
          maxLength={6}
          autoFocus
          error={errors.code?.message}
          {...register('code')}
        />
        <div className="space-y-2">
          <TextField
            label="New password"
            type="password"
            autoComplete="new-password"
            error={errors.password?.message}
            {...register('password')}
          />
          <PasswordRules password={password} />
        </div>
        <TextField
          label="Confirm new password"
          type="password"
          autoComplete="new-password"
          error={errors.confirmPassword?.message}
          {...register('confirmPassword')}
        />
        <SubmitButton loading={isSubmitting}>Update password</SubmitButton>
      </form>
      <div className="mt-6 flex items-center justify-between text-sm">
        <button type="button" onClick={onRestart} className="font-medium text-indigo-600 hover:underline">
          Use a different email
        </button>
        <Link to="/login" className="text-slate-600 hover:underline">
          Back to sign in
        </Link>
      </div>
    </AuthLayout>
  )
}
