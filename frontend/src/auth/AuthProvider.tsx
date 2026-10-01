import { useQueryClient } from '@tanstack/react-query'
import { Hub } from 'aws-amplify/utils'
import { fetchUserAttributes, getCurrentUser, signOut as amplifySignOut } from 'aws-amplify/auth'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { setUnauthorizedHandler } from '../api/client'
import { AuthContext, type AuthState } from './AuthContext'

const SIGNED_OUT: AuthState = { status: 'signedOut', user: null }

async function loadSession(): Promise<AuthState> {
  try {
    const { userId, signInDetails } = await getCurrentUser()
    const attributes = await fetchUserAttributes()
    return {
      status: 'signedIn',
      user: {
        userId,
        email: attributes.email ?? signInDetails?.loginId ?? '',
        name: attributes.name,
      },
    }
  } catch {
    // No session, an expired refresh token, or Cognito not configured.
    return SIGNED_OUT
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [state, setState] = useState<AuthState>({ status: 'loading', user: null })
  const [sessionExpired, setSessionExpired] = useState(false)

  const applySession = useCallback((next: AuthState) => {
    setState(next)
    if (next.status === 'signedIn') setSessionExpired(false)
  }, [])

  const clearSession = useCallback(() => {
    setState(SIGNED_OUT)
    // Drop every cached API response so the next user on this browser starts clean.
    queryClient.clear()
  }, [queryClient])

  const refresh = useCallback(async () => {
    applySession(await loadSession())
  }, [applySession])

  const signOut = useCallback(async () => {
    await amplifySignOut()
    setSessionExpired(false)
    clearSession()
  }, [clearSession])

  useEffect(() => {
    void loadSession().then(applySession)

    // The backend rejected our token (revoked, or the refresh token expired): end the local session too.
    setUnauthorizedHandler(() => {
      setSessionExpired(true)
      clearSession()
      void amplifySignOut().catch(() => undefined)
    })

    const stopListening = Hub.listen('auth', ({ payload }) => {
      switch (payload.event) {
        case 'signedIn':
          void refresh()
          break
        case 'tokenRefresh_failure':
          setSessionExpired(true)
          clearSession()
          break
        case 'signedOut':
          clearSession()
          break
      }
    })

    return () => {
      stopListening()
      setUnauthorizedHandler(undefined)
    }
  }, [applySession, clearSession, refresh])

  const value = useMemo(
    () => ({ ...state, sessionExpired, refresh, signOut }),
    [state, sessionExpired, refresh, signOut],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
