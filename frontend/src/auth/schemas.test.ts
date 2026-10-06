import { describe, expect, it } from 'vitest'
import { confirmSignUpSchema, forgotPasswordConfirmSchema, loginSchema, PASSWORD_RULES, registerSchema } from './schemas'

const STRONG_PASSWORD = 'Str0ng!pass'

function messages(schema: { safeParse: (value: unknown) => { success: boolean; error?: { issues: { message: string }[] } } }, value: unknown) {
  const parsed = schema.safeParse(value)
  return parsed.success ? [] : parsed.error!.issues.map((issue) => issue.message)
}

describe('PASSWORD_RULES', () => {
  const unmet = (password: string) => PASSWORD_RULES.filter((rule) => !rule.test(password)).map((rule) => rule.label)

  it('are all met by a strong password', () => {
    expect(unmet(STRONG_PASSWORD)).toEqual([])
  })

  it.each([
    ['Str0ng!', 'At least 8 characters'],
    ['str0ng!pass', 'An uppercase letter'],
    ['STR0NG!PASS', 'A lowercase letter'],
    ['Strong!pass', 'A number'],
    ['Str0ngpass', 'A symbol (e.g. ! @ # $)'],
    ['Str0ng pass', 'A symbol (e.g. ! @ # $)'],
  ])('%j fails only "%s"', (password, rule) => {
    expect(unmet(password)).toEqual([rule])
  })
})

describe('loginSchema', () => {
  it('lowercases the email, since Cognito usernames are case-sensitive', () => {
    expect(loginSchema.parse({ email: 'Ada@Example.COM', password: 'x' }).email).toBe('ada@example.com')
  })

  it('requires a valid email and a password', () => {
    expect(messages(loginSchema, { email: 'ada', password: '' })).toEqual([
      'Enter a valid email address',
      'Enter your password',
    ])
  })
})

describe('registerSchema', () => {
  const valid = { name: 'Ada', email: 'ada@example.com', password: STRONG_PASSWORD, confirmPassword: STRONG_PASSWORD }

  it('accepts a complete registration', () => {
    expect(registerSchema.safeParse(valid).success).toBe(true)
  })

  it('requires a name', () => {
    expect(messages(registerSchema, { ...valid, name: '  ' })).toEqual(['Enter your name'])
  })

  it('rejects a weak password', () => {
    expect(messages(registerSchema, { ...valid, password: 'weak', confirmPassword: 'weak' })).toEqual([
      "Password doesn't meet all the requirements",
    ])
  })

  it('rejects a confirmation that differs from the password', () => {
    expect(messages(registerSchema, { ...valid, confirmPassword: `${STRONG_PASSWORD}x` })).toEqual([
      "Passwords don't match",
    ])
  })
})

describe('verification codes', () => {
  it('accept exactly six digits, ignoring surrounding spaces', () => {
    expect(confirmSignUpSchema.parse({ email: 'ada@example.com', code: ' 123456 ' }).code).toBe('123456')
  })

  it.each(['12345', '1234567', '12a456', ''])('reject %j', (code) => {
    expect(messages(confirmSignUpSchema, { email: 'ada@example.com', code })).toEqual(['Enter the 6-digit code'])
  })

  it('are checked on the reset-password form too', () => {
    expect(
      messages(forgotPasswordConfirmSchema, { code: '12', password: STRONG_PASSWORD, confirmPassword: STRONG_PASSWORD }),
    ).toEqual(['Enter the 6-digit code'])
  })
})
