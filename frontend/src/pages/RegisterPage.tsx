import { zodResolver } from '@hookform/resolvers/zod'
import { autoSignIn, signUp } from 'aws-amplify/auth'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { authErrorMessage } from '../auth/errors'
import type { AuthLocationState } from '../auth/routes'
import { registerSchema, type RegisterValues } from '../auth/schemas'
import { AuthLayout } from '../components/AuthLayout'
import { Alert } from '../components/form/Alert'
import { PasswordRules } from '../components/form/PasswordRules'
import { SubmitButton } from '../components/form/SubmitButton'
import { TextField } from '../components/form/TextField'

export function RegisterPage() {
  const navigate = useNavigate()
  const { refresh } = useAuth()
  const [formError, setFormError] = useState<string>()

  const {
    register,
    handleSubmit,
    control,
    formState: { errors, isSubmitting },
  } = useForm<RegisterValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { name: '', email: '', password: '', confirmPassword: '' },
  })
  const password = useWatch({ control, name: 'password' })

  const onSubmit = async ({ name, email, password }: RegisterValues) => {
    setFormError(undefined)
    try {
      const { nextStep } = await signUp({
        username: email,
        password,
        options: {
          userAttributes: { email, name },
          // Lets the confirm screen sign the user in straight after they enter the code.
          autoSignIn: true,
        },
      })

      switch (nextStep.signUpStep) {
        case 'CONFIRM_SIGN_UP': {
          const state: AuthLocationState = { email }
          navigate('/confirm', { state })
          return
        }
        case 'COMPLETE_AUTO_SIGN_IN':
          // Only happens if the pool doesn't require email verification.
          await autoSignIn()
          await refresh()
          navigate('/', { replace: true })
          return
        case 'DONE': {
          const state: AuthLocationState = { email, message: 'Account created. You can sign in now.' }
          navigate('/login', { state })
          return
        }
      }
    } catch (error) {
      setFormError(authErrorMessage(error))
    }
  }

  return (
    <AuthLayout title="Create your account" subtitle="Start tracking jobs at your target companies">
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField label="Full name" autoComplete="name" autoFocus error={errors.name?.message} {...register('name')} />
        <TextField label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
        <div className="space-y-2">
          <TextField
            label="Password"
            type="password"
            autoComplete="new-password"
            error={errors.password?.message}
            {...register('password')}
          />
          <PasswordRules password={password} />
        </div>
        <TextField
          label="Confirm password"
          type="password"
          autoComplete="new-password"
          error={errors.confirmPassword?.message}
          {...register('confirmPassword')}
        />

        <SubmitButton loading={isSubmitting}>Create account</SubmitButton>
      </form>

      <p className="mt-6 text-center text-sm text-slate-600">
        Already have an account?{' '}
        <Link to="/login" className="font-medium text-indigo-600 hover:underline">
          Sign in
        </Link>
      </p>
    </AuthLayout>
  )
}
