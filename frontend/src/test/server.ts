import { setupServer } from 'msw/node'

/** Matches VITE_API_BASE_URL in the Vitest config. */
export const API = 'http://api.test'

/** Stands in for the backend. Each test declares the responses it needs with server.use(). */
export const server = setupServer()
