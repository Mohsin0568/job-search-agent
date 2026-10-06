# Job Search Agent — Frontend

React + TypeScript + Vite, with Amazon Cognito (via `aws-amplify`) for sign-in.

## Setup

```bash
npm install
cp .env.example .env.local   # then fill in the Cognito values
npm run dev                  # http://localhost:5173
```

In development, requests to `/api/*` are proxied to the Spring Boot backend on `http://localhost:8080`.

## Tests

```bash
npm test             # unit and component tests (Vitest, Testing Library, MSW)
npm run test:watch   # the same, re-running on change
npm run test:e2e     # browser tests (Playwright); first run: npx playwright install chromium
```

None of these need Cognito, the backend or `.env.local`. Unit and component tests sit next to the code
as `*.test.ts(x)`, with shared helpers in `src/test/`. The browser tests in `e2e/` start their own dev
server on port 5174 and answer Cognito and `/api` calls inside the browser (see `e2e/app.ts`).

## Layout

```
src/
  api/        fetch wrapper (adds the Cognito token, handles 401), typed endpoints, React Query hooks
  auth/       Amplify config, AuthProvider/useAuth, route guards, form schemas, error messages
  components/ layouts and shared UI
  config/     typed access to VITE_* environment variables
  hooks/      generic React hooks (e.g. useDebouncedValue)
  jobs/       job card, ATS badge, grouping and formatting of job results
  pages/      route screens
  profile/    profile form, schema, and the RequireProfile onboarding guard
  test/       test setup, the MSW server, render helper and fixtures
  router.tsx  route table
```
