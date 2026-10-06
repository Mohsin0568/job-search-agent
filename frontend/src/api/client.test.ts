import { fetchAuthSession } from 'aws-amplify/auth'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { API, server } from '../test/server'
import { ApiError, apiFetch, setUnauthorizedHandler } from './client'
import { getJobResults, getProfile, saveProfile, suggestNames } from './endpoints'

vi.mock('aws-amplify/auth', () => ({ fetchAuthSession: vi.fn() }))

function signedInWith(token: string) {
  vi.mocked(fetchAuthSession).mockResolvedValue({
    tokens: { accessToken: { toString: () => token } },
  } as Awaited<ReturnType<typeof fetchAuthSession>>)
}

/** Answers GET /echo with the request's headers, so tests can see what was sent. */
function echoHeaders() {
  server.use(http.get(`${API}/echo`, ({ request }) => HttpResponse.json(Object.fromEntries(request.headers))))
}

async function failure(promise: Promise<unknown>): Promise<ApiError> {
  const error = await promise.then(
    () => undefined,
    (caught: unknown) => caught,
  )
  expect(error).toBeInstanceOf(ApiError)
  return error as ApiError
}

beforeEach(() => signedInWith('access-token'))
afterEach(() => {
  setUnauthorizedHandler(undefined)
  vi.mocked(fetchAuthSession).mockReset()
})

describe('apiFetch', () => {
  it("sends the user's access token as a bearer token", async () => {
    echoHeaders()

    const headers = await apiFetch<Record<string, string>>('/echo')

    expect(headers.authorization).toBe('Bearer access-token')
  })

  it('sends no Authorization header when there is no session', async () => {
    vi.mocked(fetchAuthSession).mockResolvedValue({})
    echoHeaders()

    expect(await apiFetch<Record<string, string>>('/echo')).not.toHaveProperty('authorization')
  })

  it('sends no Authorization header when the session cannot be read', async () => {
    vi.mocked(fetchAuthSession).mockRejectedValue(new Error('Auth UserPool not configured'))
    echoHeaders()

    expect(await apiFetch<Record<string, string>>('/echo')).not.toHaveProperty('authorization')
  })

  it('marks a request with a body as JSON', async () => {
    server.use(
      http.put(`${API}/echo`, async ({ request }) =>
        HttpResponse.json({ contentType: request.headers.get('content-type'), body: await request.json() }),
      ),
    )

    const sent = await apiFetch('/echo', { method: 'PUT', body: JSON.stringify({ a: 1 }) })

    expect(sent).toEqual({ contentType: 'application/json', body: { a: 1 } })
  })

  it('returns undefined for an empty response, such as 202 Accepted', async () => {
    server.use(http.post(`${API}/empty`, () => new HttpResponse(null, { status: 202 })))

    expect(await apiFetch('/empty', { method: 'POST' })).toBeUndefined()
  })

  it("fails with the backend's message and status", async () => {
    server.use(http.get(`${API}/broken`, () => HttpResponse.json({ message: 'Add at least one company' }, { status: 400 })))

    const error = await failure(apiFetch('/broken'))

    expect(error.status).toBe(400)
    expect(error.message).toBe('Add at least one company')
  })

  it('fails with the raw response text when it is not the backend’s JSON error', async () => {
    server.use(http.get(`${API}/broken`, () => new HttpResponse('Bad Gateway from proxy', { status: 502 })))

    expect((await failure(apiFetch('/broken'))).message).toBe('Bad Gateway from proxy')
  })

  it('fails with the status text when the response has no body', async () => {
    server.use(
      http.get(`${API}/broken`, () => new HttpResponse(null, { status: 503, statusText: 'Service Unavailable' })),
    )

    expect((await failure(apiFetch('/broken'))).message).toBe('Service Unavailable')
  })

  it('tells the app the session is over on a 401', async () => {
    const onUnauthorized = vi.fn()
    setUnauthorizedHandler(onUnauthorized)
    server.use(http.get(`${API}/secure`, () => new HttpResponse(null, { status: 401 })))

    const error = await failure(apiFetch('/secure'))

    expect(error.status).toBe(401)
    expect(onUnauthorized).toHaveBeenCalledOnce()
  })

  it('does not end the session for other errors', async () => {
    const onUnauthorized = vi.fn()
    setUnauthorizedHandler(onUnauthorized)
    server.use(
      http.get(`${API}/forbidden`, () => new HttpResponse(null, { status: 403 })),
      http.get(`${API}/broken`, () => new HttpResponse(null, { status: 500 })),
    )

    await failure(apiFetch('/forbidden'))
    await failure(apiFetch('/broken'))

    expect(onUnauthorized).not.toHaveBeenCalled()
  })
})

describe('endpoints', () => {
  it('getProfile returns the profile', async () => {
    server.use(http.get(`${API}/api/profile`, () => HttpResponse.json({ desiredRole: 'Java Developer' })))

    expect(await getProfile()).toEqual({ desiredRole: 'Java Developer' })
  })

  it('getProfile returns null when the user has no profile yet', async () => {
    server.use(http.get(`${API}/api/profile`, () => HttpResponse.json({ message: 'No profile' }, { status: 404 })))

    expect(await getProfile()).toBeNull()
  })

  it('getProfile fails on any other error', async () => {
    server.use(http.get(`${API}/api/profile`, () => new HttpResponse(null, { status: 500 })))

    expect((await failure(getProfile())).status).toBe(500)
  })

  it('saveProfile PUTs the profile and returns the saved copy', async () => {
    server.use(
      http.put(`${API}/api/profile`, async ({ request }) =>
        HttpResponse.json({ ...((await request.json()) as object), recencyWindowDays: 30 }),
      ),
    )
    const profile = {
      desiredRole: 'Java Developer',
      skills: [],
      currentJobDescription: null,
      companyPreferences: ['Acme Corp'],
      recencyWindowDays: 7,
    }

    expect(await saveProfile(profile)).toEqual({ ...profile, recencyWindowDays: 30 })
  })

  it('getJobResults asks for the given page', async () => {
    server.use(
      http.get(`${API}/api/jobs/results`, ({ request }) => {
        const params = new URL(request.url).searchParams
        return HttpResponse.json([{ id: `page ${params.get('page')}, size ${params.get('size')}` }])
      }),
    )

    expect(await getJobResults(2, 25)).toEqual([{ id: 'page 2, size 25' }])
    expect(await getJobResults()).toEqual([{ id: 'page 0, size 50' }])
  })

  it('suggestNames queries the list for the source, encoding what was typed', async () => {
    server.use(
      http.get(`${API}/api/skills/suggest`, ({ request }) =>
        HttpResponse.json([{ name: new URL(request.url).searchParams.get('q') }]),
      ),
    )

    expect(await suggestNames('skills', 'c++ & c#')).toEqual([{ name: 'c++ & c#' }])
  })
})
