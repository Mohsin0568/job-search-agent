const read = (key: string): string => (import.meta.env[key] as string | undefined)?.trim() ?? ''

export const env = {
  cognitoRegion: read('VITE_COGNITO_REGION'),
  cognitoUserPoolId: read('VITE_COGNITO_USER_POOL_ID'),
  cognitoClientId: read('VITE_COGNITO_CLIENT_ID'),
  apiBaseUrl: read('VITE_API_BASE_URL'),
  // Only the register page's "invite-only for now" note: the backend decides who is let in (INVITE_ONLY).
  inviteOnly: read('VITE_INVITE_ONLY') !== 'false',
}

// Placeholder values from .env.example count as "not configured".
export const isCognitoConfigured =
  env.cognitoUserPoolId !== '' &&
  env.cognitoClientId !== '' &&
  !env.cognitoUserPoolId.includes('XXXX') &&
  !env.cognitoClientId.startsWith('xxxx')
