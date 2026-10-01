import { Amplify } from 'aws-amplify'
import { env, isCognitoConfigured } from '../config/env'

export function configureAmplify(): void {
  if (!isCognitoConfigured) {
    console.warn(
      'Cognito is not configured. Set VITE_COGNITO_USER_POOL_ID and VITE_COGNITO_CLIENT_ID in .env.local.',
    )
    return
  }

  Amplify.configure({
    Auth: {
      Cognito: {
        userPoolId: env.cognitoUserPoolId,
        userPoolClientId: env.cognitoClientId,
        loginWith: { email: true },
        signUpVerificationMethod: 'code',
      },
    },
  })
}
