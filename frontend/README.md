# Job Search Agent — Frontend

React + TypeScript + Vite, with Amazon Cognito (via `aws-amplify`) for sign-in.

## Setup

```bash
npm install
cp .env.example .env.local   # then fill in the Cognito values
npm run dev                  # http://localhost:5173
```

In development, requests to `/api/*` are proxied to the Spring Boot backend on `http://localhost:8080`.

## Layout

```
src/
  api/        fetch wrapper (adds the Cognito token, handles 401), typed endpoints, React Query hooks
  auth/       Amplify config, AuthProvider/useAuth, route guards, form schemas, error messages
  components/ layouts and shared UI
  config/     typed access to VITE_* environment variables
  jobs/       job card, ATS badge, grouping and formatting of job results
  pages/      route screens
  profile/    profile form, schema, and the RequireProfile onboarding guard
  router.tsx  route table
```
