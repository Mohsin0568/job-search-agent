const MESSAGES: Record<string, string> = {
  UsernameExistsException: 'An account with this email already exists. Try signing in instead.',
  NotAuthorizedException: 'Incorrect email or password.',
  UserNotFoundException: 'Incorrect email or password.',
  CodeMismatchException: "That code isn't right. Check the email and try again.",
  ExpiredCodeException: 'That code has expired. Request a new one.',
  InvalidPasswordException: "The password doesn't meet the requirements.",
  LimitExceededException: 'Too many attempts. Wait a few minutes and try again.',
  TooManyRequestsException: 'Too many attempts. Wait a few minutes and try again.',
  TooManyFailedAttemptsException: 'Too many failed attempts. Wait a few minutes and try again.',
  CodeDeliveryFailureException: "We couldn't send the verification email. Try again shortly.",
  InvalidParameterException: 'Some of the details entered are invalid.',
  AuthUserPoolException: "Sign-in isn't configured yet (missing Cognito settings).",
  NetworkError: "Can't reach the sign-in service. Check your connection.",
}

// Cognito reuses NotAuthorizedException for a few non-password cases; its message tells them apart.
const NOT_AUTHORIZED_OVERRIDES: Array<[RegExp, string]> = [
  [/attempts exceeded/i, 'Too many failed attempts. Wait a few minutes and try again.'],
  [/already confirmed/i, 'This account is already verified. You can sign in.'],
]

export function authErrorMessage(error: unknown): string {
  if (!(error instanceof Error)) return 'Something went wrong. Please try again.'

  if (error.name === 'NotAuthorizedException') {
    const override = NOT_AUTHORIZED_OVERRIDES.find(([pattern]) => pattern.test(error.message))
    if (override) return override[1]
  }
  return MESSAGES[error.name] ?? error.message ?? 'Something went wrong. Please try again.'
}
