import { describe, expect, it } from 'vitest'
import { namedError } from '../test/render'
import { authErrorMessage } from './errors'

describe('authErrorMessage', () => {
  it.each([
    ['UsernameExistsException', 'An account with this email already exists. Try signing in instead.'],
    ['CodeMismatchException', "That code isn't right. Check the email and try again."],
    ['ExpiredCodeException', 'That code has expired. Request a new one.'],
    ['LimitExceededException', 'Too many attempts. Wait a few minutes and try again.'],
    ['NetworkError', "Can't reach the sign-in service. Check your connection."],
  ])('explains %s in plain words', (name, message) => {
    expect(authErrorMessage(namedError(name, 'raw Cognito text'))).toBe(message)
  })

  it('gives the same message for a wrong password and an unknown email', () => {
    const wrongPassword = authErrorMessage(namedError('NotAuthorizedException', 'Incorrect username or password.'))
    const unknownEmail = authErrorMessage(namedError('UserNotFoundException', 'User does not exist.'))

    expect(wrongPassword).toBe('Incorrect email or password.')
    expect(unknownEmail).toBe(wrongPassword)
  })

  it('tells apart the other cases Cognito reports as NotAuthorizedException', () => {
    expect(authErrorMessage(namedError('NotAuthorizedException', 'Password attempts exceeded'))).toBe(
      'Too many failed attempts. Wait a few minutes and try again.',
    )
    expect(authErrorMessage(namedError('NotAuthorizedException', 'User is already confirmed.'))).toBe(
      'This account is already verified. You can sign in.',
    )
  })

  it("falls back to the error's own message for an unknown error", () => {
    expect(authErrorMessage(namedError('SomethingNewException', 'A brand new failure'))).toBe('A brand new failure')
  })

  it.each(['a string', null, undefined, { name: 'NotAuthorizedException' }])(
    'gives a generic message for %j, which is not an Error',
    (value) => {
      expect(authErrorMessage(value)).toBe('Something went wrong. Please try again.')
    },
  )
})
