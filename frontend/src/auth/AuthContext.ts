import { createContext, useContext } from 'react'

export type AuthUser = {
  userId: string // Cognito "sub" - the same value the backend will read from the JWT
  email: string
  name?: string
}

export type AuthState =
  | { status: 'loading'; user: null }
  | { status: 'signedOut'; user: null }
  | { status: 'signedIn'; user: AuthUser }

export type AuthContextValue = AuthState & {
  /** True after the session ended without the user signing out (e.g. the API returned 401). */
  sessionExpired: boolean
  /** Re-reads the current session from Amplify. Await it before navigating after sign-in. */
  refresh: () => Promise<void>
  signOut: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext)
  if (!value) {
    throw new Error('useAuth must be used inside <AuthProvider>')
  }
  return value
}
