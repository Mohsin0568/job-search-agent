import { fetchAuthSession } from 'aws-amplify/auth'
import { env } from '../config/env'

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

let onUnauthorized: (() => void) | undefined

/** Called once by AuthProvider so a 401 from the API ends the local session. */
export function setUnauthorizedHandler(handler: (() => void) | undefined): void {
  onUnauthorized = handler
}

async function accessToken(): Promise<string | undefined> {
  try {
    // Amplify refreshes the access token automatically when it has expired.
    const session = await fetchAuthSession()
    return session.tokens?.accessToken?.toString()
  } catch {
    return undefined
  }
}

// The backend returns { "message": "..." } for handled errors; fall back to the raw text otherwise.
function errorMessage(body: string, fallback: string): string {
  try {
    const parsed = JSON.parse(body) as { message?: unknown }
    if (typeof parsed.message === 'string' && parsed.message) return parsed.message
  } catch {
    // Not JSON.
  }
  return body || fallback
}

export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  const token = await accessToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(`${env.apiBaseUrl}${path}`, { ...init, headers })
  const body = await response.text()

  if (!response.ok) {
    if (response.status === 401) {
      onUnauthorized?.()
    }
    throw new ApiError(response.status, errorMessage(body, response.statusText))
  }
  // 202/204 and other empty responses.
  return (body ? JSON.parse(body) : undefined) as T
}
