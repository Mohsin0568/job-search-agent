import { createBrowserRouter } from 'react-router-dom'
import { ProtectedRoute, PublicOnlyRoute } from './auth/routes'
import { AppLayout } from './components/AppLayout'
import { ConfirmSignUpPage } from './pages/ConfirmSignUpPage'
import { DashboardPage } from './pages/DashboardPage'
import { ForgotPasswordPage } from './pages/ForgotPasswordPage'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { OnboardingPage } from './pages/OnboardingPage'
import { RegisterPage } from './pages/RegisterPage'
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
        element: <AppLayout />,
        children: [
          { path: '/onboarding', element: <OnboardingPage /> },
          {
            element: <RequireProfile />,
            children: [{ index: true, element: <DashboardPage /> }],
          },
        ],
      },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
])
