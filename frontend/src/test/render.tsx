import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { createMemoryRouter, RouterProvider, type InitialEntry, type RouteObject } from 'react-router-dom'
import { vi } from 'vitest'
import { AuthContext, type AuthContextValue, type AuthUser } from '../auth/AuthContext'

export const TEST_USER: AuthUser = { userId: 'user-1', email: 'ada@example.com', name: 'Ada Lovelace' }

type Options = {
  /** Where the router starts; pass an object to include location state. */
  at?: InitialEntry
  /** Overrides for the auth context. The default is a signed-in TEST_USER. */
  auth?: Partial<AuthContextValue>
}

/**
 * Renders routes with the providers the app wraps them in (see main.tsx), but with a fake auth
 * context and an in-memory router. Read `router.state.location` to assert where the page navigated.
 */
export function renderRoutes(routes: RouteObject[], { at = '/', auth }: Options = {}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const authValue = {
    status: 'signedIn',
    user: TEST_USER,
    sessionExpired: false,
    refresh: vi.fn(async () => undefined),
    signOut: vi.fn(async () => undefined),
    ...auth,
  } as AuthContextValue
  const router = createMemoryRouter(routes, { initialEntries: [at] })

  render(
    <QueryClientProvider client={queryClient}>
      <AuthContext.Provider value={authValue}>
        <RouterProvider router={router} />
      </AuthContext.Provider>
    </QueryClientProvider>,
  )
  return { router, queryClient, auth: authValue }
}

/** An Error with the given name, the way Amplify reports Cognito failures. */
export function namedError(name: string, message = name): Error {
  return Object.assign(new Error(message), { name })
}
