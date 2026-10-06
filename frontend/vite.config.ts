import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    // Forward /api calls to the Spring Boot backend during development,
    // so the browser sees a single origin and no CORS setup is needed locally.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    include: ['src/**/*.test.{ts,tsx}'],
    // Fixed values so tests don't depend on .env.local. Node's fetch needs an absolute API URL.
    env: {
      VITE_COGNITO_REGION: 'eu-west-2',
      VITE_COGNITO_USER_POOL_ID: 'eu-west-2_testPool1',
      VITE_COGNITO_CLIENT_ID: 'test-client',
      VITE_API_BASE_URL: 'http://api.test',
    },
  },
})
