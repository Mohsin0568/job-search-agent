const read = (key: string): string => (import.meta.env[key] as string | undefined)?.trim() ?? ''

export const env = {
  cognitoRegion: read('VITE_COGNITO_REGION'),
  cognitoUserPoolId: read('VITE_COGNITO_USER_POOL_ID'),
  cognitoClientId: read('VITE_COGNITO_CLIENT_ID'),
  apiBaseUrl: read('VITE_API_BASE_URL'),
}

// Placeholder values from .env.example count as "not configured".
export const isCognitoConfigured =
  env.cognitoUserPoolId !== '' &&
  env.cognitoClientId !== '' &&
  !env.cognitoUserPoolId.includes('XXXX') &&
  !env.cognitoClientId.startsWith('xxxx')
