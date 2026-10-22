import { fetchAuthSession } from 'aws-amplify/auth'
import { env } from '../config/env'

export class ApiError extends Error {
  readonly status: number
  /** A fixed name the backend gives errors the app acts on, e.g. ACCESS_PENDING. */
  readonly code: string | undefined

  constructor(status: number, message: string, code?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

/** The codes of a 403 for a user not let in while the app is invite-only. */
export const ACCESS_DENIED_CODES = ['ACCESS_PENDING', 'ACCESS_REVOKED']

let onUnauthorized: (() => void) | undefined
let onAccessDenied: (() => void) | undefined

/** Called once by AuthProvider so a 401 from the API ends the local session. */
export function setUnauthorizedHandler(handler: (() => void) | undefined): void {
  onUnauthorized = handler
}

/** Called by RequireAccess so a 403 for a user not let in shows the waitlist, whichever request got it. */
export function setAccessDeniedHandler(handler: (() => void) | undefined): void {
  onAccessDenied = handler
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

// The backend returns { "message": "...", "code"?: "..." } for handled errors; fall back to the raw text.
function errorDetails(body: string, fallback: string): { message: string; code?: string } {
  try {
    const parsed = JSON.parse(body) as { message?: unknown; code?: unknown }
    const code = typeof parsed.code === 'string' ? parsed.code : undefined
    if (typeof parsed.message === 'string' && parsed.message) return { message: parsed.message, code }
    return { message: body || fallback, code }
  } catch {
    // Not JSON.
  }
  return { message: body || fallback }
}

export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  const token = await accessToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  // A FormData body (a file upload) must be left alone: fetch adds the multipart boundary itself.
  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(`${env.apiBaseUrl}${path}`, { ...init, headers })
  const body = await response.text()

  if (!response.ok) {
    const { message, code } = errorDetails(body, response.statusText)
    if (response.status === 401) {
      onUnauthorized?.()
    }
    if (response.status === 403 && code && ACCESS_DENIED_CODES.includes(code)) {
      onAccessDenied?.()
    }
    throw new ApiError(response.status, message, code)
  }
  // 202/204 and other empty responses.
  return (body ? JSON.parse(body) : undefined) as T
}
