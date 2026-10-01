import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router-dom'
import { ApiError } from './api/client'
import { configureAmplify } from './auth/amplify'
import { AuthProvider } from './auth/AuthProvider'
import './index.css'
import { router } from './router'

configureAmplify()

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Retry once on network/5xx errors; 4xx responses won't change on retry.
      retry: (failureCount, error) => failureCount < 1 && !(error instanceof ApiError && error.status < 500),
      refetchOnWindowFocus: false,
    },
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>
  </StrictMode>,
)
