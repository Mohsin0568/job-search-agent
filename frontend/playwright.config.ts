import { defineConfig, devices } from '@playwright/test'

// A port of its own, so the tests never talk to a dev server started by hand with real settings.
const PORT = 5174

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: `http://localhost:${PORT}`,
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: `npm run dev -- --port ${PORT} --strictPort`,
    url: `http://localhost:${PORT}`,
    reuseExistingServer: false,
    // Cognito and the API are both faked in the browser (see e2e/app.ts), so these only need to
    // look real. Variables set here win over .env.local.
    env: {
      VITE_COGNITO_REGION: 'eu-west-2',
      VITE_COGNITO_USER_POOL_ID: 'eu-west-2_e2ePool01',
      VITE_COGNITO_CLIENT_ID: 'e2e-client',
      VITE_API_BASE_URL: '',
    },
  },
})
