import { createBrowserRouter } from 'react-router-dom'
import { RequireAccess } from './access/RequireAccess'
import { ProtectedRoute, PublicOnlyRoute } from './auth/routes'
import { AppLayout } from './components/AppLayout'
import { ConfirmSignUpPage } from './pages/ConfirmSignUpPage'
import { DashboardPage } from './pages/DashboardPage'
import { ForgotPasswordPage } from './pages/ForgotPasswordPage'
import { LoginPage } from './pages/LoginPage'
import { MarkedJobsPage } from './pages/MarkedJobsPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { OnboardingPage } from './pages/OnboardingPage'
import { ProfilePage } from './pages/ProfilePage'
import { RegisterPage } from './pages/RegisterPage'
import { SearchHistoryPage } from './pages/SearchHistoryPage'
import { RequireProfile } from './profile/RequireProfile'

export const router = createBrowserRouter([
  {
    element: <PublicOnlyRoute />,
    children: [
      { path: '/login', element: <LoginPage /> },
      { path: '/register', element: <RegisterPage /> },
      { path: '/confirm', element: <ConfirmSignUpPage /> },
      { path: '/forgot-password', element: <ForgotPasswordPage /> },
    ],
  },
  {
    element: <ProtectedRoute />,
    children: [
      {
        // Before the app itself: a user not let in while it is invite-only sees the waitlist instead.
        element: <RequireAccess />,
        children: [
          {
            element: <AppLayout />,
            children: [
              { path: '/onboarding', element: <OnboardingPage /> },
              {
                element: <RequireProfile />,
                children: [
                  { index: true, element: <DashboardPage /> },
                  { path: '/marked', element: <MarkedJobsPage /> },
                  { path: '/history', element: <SearchHistoryPage /> },
                  { path: '/profile', element: <ProfilePage /> },
                ],
              },
            ],
          },
        ],
      },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
])
