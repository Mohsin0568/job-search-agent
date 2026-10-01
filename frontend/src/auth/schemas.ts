import { z } from 'zod'

// Mirrors the default Cognito password policy. Keep in sync with the User Pool settings.
export const PASSWORD_RULES: Array<{ label: string; test: (value: string) => boolean }> = [
  { label: 'At least 8 characters', test: (v) => v.length >= 8 },
  { label: 'An uppercase letter', test: (v) => /[A-Z]/.test(v) },
  { label: 'A lowercase letter', test: (v) => /[a-z]/.test(v) },
  { label: 'A number', test: (v) => /\d/.test(v) },
  { label: 'A symbol (e.g. ! @ # $)', test: (v) => /[^A-Za-z0-9\s]/.test(v) },
]

const email = z.email('Enter a valid email address').trim().toLowerCase()

const newPassword = z
  .string()
  .refine((v) => PASSWORD_RULES.every((rule) => rule.test(v)), "Password doesn't meet all the requirements")

const code = z
  .string()
  .trim()
  .regex(/^\d{6}$/, 'Enter the 6-digit code')

export const loginSchema = z.object({
  email,
  password: z.string().min(1, 'Enter your password'),
})

export const registerSchema = z
  .object({
    name: z.string().trim().min(1, 'Enter your name'),
    email,
    password: newPassword,
    confirmPassword: z.string(),
  })
  .refine((v) => v.password === v.confirmPassword, {
    path: ['confirmPassword'],
    message: "Passwords don't match",
  })

export const confirmSignUpSchema = z.object({ email, code })

export const forgotPasswordRequestSchema = z.object({ email })

export const forgotPasswordConfirmSchema = z
  .object({
    code,
    password: newPassword,
    confirmPassword: z.string(),
  })
  .refine((v) => v.password === v.confirmPassword, {
    path: ['confirmPassword'],
    message: "Passwords don't match",
  })

export type LoginValues = z.infer<typeof loginSchema>
export type RegisterValues = z.infer<typeof registerSchema>
export type ConfirmSignUpValues = z.infer<typeof confirmSignUpSchema>
export type ForgotPasswordRequestValues = z.infer<typeof forgotPasswordRequestSchema>
export type ForgotPasswordConfirmValues = z.infer<typeof forgotPasswordConfirmSchema>
