import { fetchAuthSession } from 'aws-amplify/auth'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { API, server } from '../test/server'
import { ApiError, apiFetch, setAccessDeniedHandler, setUnauthorizedHandler } from './client'
import {
  getJobResults,
  getProfile,
  getResumeDownload,
  getResumeVersions,
  getTailoredResumeDownload,
  getTailoredResumes,
  requestTailoredResume,
  templatePreviewUrl,
  saveProfile,
  suggestNames,
  uploadResume,
} from './endpoints'

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
  setAccessDeniedHandler(undefined)
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

  it('leaves the content type of a file upload to fetch, which adds the multipart boundary', async () => {
    server.use(
      http.post(`${API}/echo`, ({ request }) => HttpResponse.json({ contentType: request.headers.get('content-type') })),
    )
    const body = new FormData()
    body.append('file', new File(['%PDF-'], 'cv.pdf', { type: 'application/pdf' }))

    const sent = await apiFetch<{ contentType: string }>('/echo', { method: 'POST', body })

    expect(sent.contentType).toMatch(/^multipart\/form-data; boundary=/)
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

  it('keeps the code the backend gives an error', async () => {
    server.use(
      http.get(`${API}/coded`, () => HttpResponse.json({ message: 'on the waitlist', code: 'ACCESS_PENDING' }, { status: 403 })),
      http.get(`${API}/plain`, () => HttpResponse.json({ message: 'bad request' }, { status: 400 })),
    )

    const coded = await failure(apiFetch('/coded'))
    expect(coded.message).toBe('on the waitlist')
    expect(coded.code).toBe('ACCESS_PENDING')
    expect((await failure(apiFetch('/plain'))).code).toBeUndefined()
  })

  it('tells the app the user is not let in on a 403 with an access code, and only then', async () => {
    const onAccessDenied = vi.fn()
    setAccessDeniedHandler(onAccessDenied)
    server.use(
      http.get(`${API}/pending`, () => HttpResponse.json({ message: 'waitlist', code: 'ACCESS_PENDING' }, { status: 403 })),
      http.get(`${API}/revoked`, () => HttpResponse.json({ message: 'removed', code: 'ACCESS_REVOKED' }, { status: 403 })),
      http.get(`${API}/other-403`, () => HttpResponse.json({ message: 'not yours' }, { status: 403 })),
    )

    await failure(apiFetch('/pending'))
    await failure(apiFetch('/revoked'))
    expect(onAccessDenied).toHaveBeenCalledTimes(2)

    await failure(apiFetch('/other-403'))
    expect(onAccessDenied).toHaveBeenCalledTimes(2)
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

  it('getResumeVersions returns the versions, newest first as the backend sends them', async () => {
    const versions = [
      { version: 2, latest: true, fileName: 'cv-2.pdf', sizeBytes: 2048, uploadedAt: '2026-10-12T09:00:00Z' },
      { version: 1, latest: false, fileName: 'cv-1.pdf', sizeBytes: 1024, uploadedAt: '2026-10-01T09:00:00Z' },
    ]
    server.use(http.get(`${API}/api/profile/resume`, () => HttpResponse.json(versions)))

    await expect(getResumeVersions()).resolves.toEqual(versions)
  })

  it('uploadResume POSTs the file as the "file" part and returns the new version', async () => {
    const created = { version: 1, latest: true, fileName: 'cv.pdf', sizeBytes: 5, uploadedAt: '2026-10-12T09:00:00Z' }
    let received = ''
    server.use(
      http.post(`${API}/api/profile/resume`, async ({ request }) => {
        received = await request.text()
        return HttpResponse.json(created, { status: 201 })
      }),
    )

    const uploaded = await uploadResume(new File(['%PDF-1.7 resume'], 'cv.pdf', { type: 'application/pdf' }))

    expect(uploaded).toEqual(created)
    // The file's name and bytes aren't checked here: jsdom's File reaches Node's fetch as an empty,
    // unnamed blob. The e2e tests, which run in a real browser, cover both.
    expect(received).toContain('Content-Disposition: form-data; name="file"; filename=')
    expect(received).toContain('Content-Type: application/pdf')
  })

  it('getResumeDownload asks for a link to the given version', async () => {
    const link = { url: 'https://storage.test/v3', expiresAt: '2026-10-12T09:05:00Z' }
    server.use(http.get(`${API}/api/profile/resume/3/download`, () => HttpResponse.json(link)))

    await expect(getResumeDownload(3)).resolves.toEqual(link)
  })

  it('getTailoredResumes returns every tailored resume the user has', async () => {
    const tailored = [
      { jobResultId: 'r-1', status: 'READY', resumeVersion: 3, template: 'MODERN', failureReason: null, requestedAt: '2026-10-13T09:00:00Z', completedAt: '2026-10-13T09:00:40Z' },
    ]
    server.use(http.get(`${API}/api/jobs/tailored-resumes`, () => HttpResponse.json(tailored)))

    await expect(getTailoredResumes()).resolves.toEqual(tailored)
  })

  it('requestTailoredResume POSTs for the job, encoding its id, and returns the pending record', async () => {
    const pending = { jobResultId: 'r/1', status: 'PENDING', resumeVersion: 3, template: 'MODERN', failureReason: null, requestedAt: '2026-10-13T09:00:00Z', completedAt: null }
    let path = ''
    let body: unknown
    let contentType: string | null = null
    server.use(
      http.post(`${API}/api/jobs/results/:id/tailored-resume`, async ({ request }) => {
        path = new URL(request.url).pathname
        contentType = request.headers.get('Content-Type')
        body = await request.json()
        return HttpResponse.json(pending, { status: 202 })
      }),
    )

    await expect(requestTailoredResume('r/1', { template: 'MODERN' })).resolves.toEqual(pending)
    expect(path).toBe('/api/jobs/results/r%2F1/tailored-resume')
    expect(contentType).toBe('application/json')
    expect(body).toEqual({ template: 'MODERN' })
  })

  it('requestTailoredResume without a template leaves the choice to the server', async () => {
    let body: unknown
    server.use(
      http.post(`${API}/api/jobs/results/:id/tailored-resume`, async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({}, { status: 202 })
      }),
    )

    await requestTailoredResume('r-1')
    expect(body).toEqual({})
  })

  it('requestTailoredResume sends what to fix', async () => {
    let body: unknown
    server.use(
      http.post(`${API}/api/jobs/results/:id/tailored-resume`, async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({}, { status: 202 })
      }),
    )

    await requestTailoredResume('r-1', { template: 'EVEN', fix: { keywords: ['Kubernetes'], redFlags: true } })
    expect(body).toEqual({ template: 'EVEN', fix: { keywords: ['Kubernetes'], redFlags: true } })
  })

  it('templatePreviewUrl points at the API’s picture of the template', () => {
    expect(templatePreviewUrl('STACK_OVERFLOW')).toBe(`${API}/api/resume-templates/STACK_OVERFLOW/preview`)
  })

  it('getTailoredResumeDownload asks for a link to the job’s tailored resume', async () => {
    const link = { url: 'https://storage.test/tailored/r-1', expiresAt: '2026-10-13T09:05:00Z' }
    server.use(http.get(`${API}/api/jobs/results/r-1/tailored-resume/download`, () => HttpResponse.json(link)))

    await expect(getTailoredResumeDownload('r-1')).resolves.toEqual(link)
  })
})
