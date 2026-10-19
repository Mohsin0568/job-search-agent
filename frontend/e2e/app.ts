import { expect, test as base, type Page, type Route } from '@playwright/test'

/**
 * Runs the real frontend against a fake Cognito and a fake backend, both answered inside the
 * browser. Nothing leaves the machine, and no account or running backend is needed.
 */

export const USER = { sub: 'e2e-user-1', email: 'ada@example.com', name: 'Ada Lovelace', password: 'Str0ng!pass' }

export type Profile = {
  desiredRole: string
  skills: string[]
  currentJobDescription: string | null
  companyPreferences: string[]
  recencyWindowDays: number
}

type Job = {
  jobId: string
  jobTitle: string
  url: string | null
  location: string
  datePosted: string
  lastDateForSubmission: string
  salaryRange: string
  source: string
  atsScore: number | null
}

export type Resume = { version: number; latest: boolean; fileName: string; sizeBytes: number; uploadedAt: string }

export type TailoredResume = {
  jobResultId: string
  status: 'PENDING' | 'READY' | 'FAILED'
  resumeVersion: number
  template: 'CLASSIC' | 'MODERN' | 'COMPACT' | 'EVEN' | 'STACK_OVERFLOW' | 'KENDALL'
  failureReason: string | null
  requestedAt: string
  completedAt: string | null
  fix: { keywords: string[]; redFlags: Array<{ issue: string; explanation: string }> } | null
}

export type ResumeReview = {
  jobResultId: string
  status: 'PENDING' | 'READY' | 'FAILED'
  resumeVersion: number
  resumeAtsScore: number | null
  missingKeywords: string[] | null
  redFlags: Array<{ issue: string; explanation: string }> | null
  failureReason: string | null
  requestedAt: string
  reviewedAt: string | null
}

export type SearchRun = {
  runId: string
  runDate: string
  startedAt: string
  finishedAt: string
  status: 'SUCCEEDED' | 'PARTIAL' | 'FAILED' | 'SKIPPED'
  companiesSearched: number
  newJobs: number
  duplicateJobs: number
  filteredOutJobs: number
  sourcesFailed: number
}

export type SearchRunCompany = {
  companyName: string
  searchedAt: string
  newJobs: number
  duplicateJobs: number
  filteredOutJobs: number
  sourcesFailed: number
  sources: Array<{
    source: string
    status: 'SCANNED' | 'FAILED'
    jobsFound: number
    newJobs: number
    duplicateJobs: number
    filteredOutJobs: number
  }>
}

/** What every review the fake backend finishes finds. */
export const REVIEW_FINDINGS = {
  resumeAtsScore: 72,
  missingKeywords: ['Kubernetes', 'Terraform', 'gRPC'],
  redFlags: [{ issue: 'No measurable results', explanation: 'Bullets list duties, not outcomes.' }],
}

/** Where the fake backend's download links point; answered by the fake storage below. */
const STORAGE = 'https://resumes.e2e.test'

/** A 1×1 PNG, standing in for every template preview. */
const PREVIEW_PNG =
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=='

export type Backend = {
  /** null means the user hasn't set up a profile yet (the API answers 404). */
  profile: Profile | null
  results: Array<{ id: string; userId: string; jobRunDateTime: string; companyName: string; job: Job }>
  companies: string[]
  skills: string[]
  /** The user's resume versions, newest first. */
  resumes: Resume[]
  /** The user's tailored resumes, one per job. A pending one is ready the next time the list is read. */
  tailoredResumes: TailoredResume[]
  /** The user's resume reviews, one per job. A pending one is ready the next time the list is read. */
  resumeReviews: ResumeReview[]
  /** The user's search history, newest run first. */
  searchRuns: SearchRun[]
  /** What each run found, by runId. A run missing here has no companies. */
  searchRunCompanies: Record<string, SearchRunCompany[]>
  /** Every profile the app has saved, oldest first. */
  savedProfiles: Profile[]
  /** When true the API rejects the token, as it does once a session has been revoked. */
  rejectTokens: boolean
  /** When true Cognito refuses the sign-in, as it does for a wrong password. */
  rejectPassword: boolean
}

export const PROFILE: Profile = {
  desiredRole: 'Senior Java Developer',
  skills: ['Java', 'Spring Boot'],
  currentJobDescription: 'Builds APIs',
  companyPreferences: ['Acme Corp', 'Globex'],
  recencyWindowDays: 14,
}

export function jobResult(id: string, companyName: string, job: Partial<Job> = {}): Backend['results'][number] {
  return {
    id,
    userId: USER.sub,
    jobRunDateTime: '2026-08-18T12:00:00',
    companyName,
    job: {
      jobId: id,
      jobTitle: 'Backend Engineer',
      url: `https://jobs.example.com/${id}`,
      location: 'London, UK',
      datePosted: '10 Aug 2026',
      lastDateForSubmission: '20 Aug 2026',
      salaryRange: '£50,000 - £60,000',
      source: 'LinkedIn',
      atsScore: 85,
      ...job,
    },
  }
}

// Amplify reads the claims but can't check the signature, so an unsigned token is enough.
function jwt(claims: Record<string, unknown>): string {
  const encode = (value: unknown) => Buffer.from(JSON.stringify(value)).toString('base64url')
  return `${encode({ alg: 'RS256', kid: 'e2e' })}.${encode(claims)}.${Buffer.from('signature').toString('base64url')}`
}

function tokens() {
  const now = Math.floor(Date.now() / 1000)
  const common = { sub: USER.sub, iss: 'https://cognito-idp.eu-west-2.amazonaws.com/eu-west-2_e2ePool01', iat: now, exp: now + 3600 }
  return {
    AccessToken: jwt({ ...common, token_use: 'access', client_id: 'e2e-client', username: USER.sub }),
    IdToken: jwt({ ...common, token_use: 'id', aud: 'e2e-client', 'cognito:username': USER.sub, email: USER.email }),
    RefreshToken: 'e2e-refresh-token',
    ExpiresIn: 3600,
    TokenType: 'Bearer',
  }
}

function cognitoError(route: Route, type: string, message: string) {
  return route.fulfill({
    status: 400,
    contentType: 'application/x-amz-json-1.1',
    headers: { 'x-amzn-errortype': type },
    body: JSON.stringify({ __type: type, message }),
  })
}

/**
 * Answers the Cognito calls Amplify makes. Sign-in uses SRP, where the password never goes over the
 * wire, so the fake can't check it: a test says up front whether the sign-in should be refused.
 */
async function fakeCognito(page: Page, backend: Backend) {
  await page.route('https://cognito-idp.eu-west-2.amazonaws.com/**', async (route) => {
    const request = route.request()
    const action = (request.headers()['x-amz-target'] ?? '').split('.').pop()
    const body = (request.postDataJSON() ?? {}) as { AuthFlow?: string; Username?: string }
    const json = (payload: unknown) =>
      route.fulfill({ status: 200, contentType: 'application/x-amz-json-1.1', body: JSON.stringify(payload) })

    switch (action) {
      case 'InitiateAuth':
        if (body.AuthFlow !== 'USER_SRP_AUTH') return json({ AuthenticationResult: tokens(), ChallengeParameters: {} })
        return json({
          ChallengeName: 'PASSWORD_VERIFIER',
          ChallengeParameters: {
            SALT: 'a1b2c3d4e5f60718293a4b5c6d7e8f90',
            SECRET_BLOCK: Buffer.from('e2e-secret-block').toString('base64'),
            SRP_B: 'abcdef0123456789'.repeat(48),
            USERNAME: USER.sub,
            USER_ID_FOR_SRP: USER.sub,
          },
        })
      case 'RespondToAuthChallenge':
        if (backend.rejectPassword) {
          return cognitoError(route, 'NotAuthorizedException', 'Incorrect username or password.')
        }
        return json({ AuthenticationResult: tokens(), ChallengeParameters: {} })
      case 'GetUser':
        return json({
          Username: USER.sub,
          UserAttributes: [
            { Name: 'sub', Value: USER.sub },
            { Name: 'email', Value: USER.email },
            { Name: 'email_verified', Value: 'true' },
            { Name: 'name', Value: USER.name },
          ],
        })
      case 'SignUp':
        return json({
          UserConfirmed: false,
          UserSub: USER.sub,
          CodeDeliveryDetails: { AttributeName: 'email', DeliveryMedium: 'EMAIL', Destination: 'a***@e***' },
        })
      case 'ConfirmSignUp':
        return json({})
      case 'RevokeToken':
      case 'GlobalSignOut':
        return json({})
      default:
        return cognitoError(route, 'InvalidParameterException', `The e2e fake Cognito has no answer for ${action}`)
    }
  })
}

async function fakeBackend(page: Page, backend: Backend) {
  // By path, not a glob: "**/api/**" would also catch the dev server's own /src/api/*.ts modules.
  await page.route((url) => url.pathname.startsWith('/api/'), async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const json = (payload: unknown, status = 200) =>
      route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(payload) })

    // Template previews are open to anyone, as on the real API: they're loaded by plain <img> tags.
    if (request.method() === 'GET' && /^\/api\/resume-templates\/[A-Z_]+\/preview$/.test(url.pathname)) {
      return route.fulfill({ status: 200, contentType: 'image/png', body: Buffer.from(PREVIEW_PNG, 'base64') })
    }

    // Like the real API, refuse anything that doesn't carry the signed-in user's access token.
    const token = request.headers()['authorization']?.replace('Bearer ', '') ?? ''
    const claims = token.includes('.') ? JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString()) : {}
    if (backend.rejectTokens || claims.token_use !== 'access' || claims.sub !== USER.sub) {
      return route.fulfill({ status: 401 })
    }

    const suggest = url.pathname.match(/^\/api\/(companies|skills)\/suggest$/)
    if (suggest) {
      const typed = (url.searchParams.get('q') ?? '').toLowerCase()
      const names = backend[suggest[1] as 'companies' | 'skills']
      return json(names.filter((name) => name.toLowerCase().startsWith(typed)).map((name) => ({ name })))
    }
    if (url.pathname === '/api/profile' && request.method() === 'GET') {
      return backend.profile ? json(backend.profile) : json({ message: 'Profile not found' }, 404)
    }
    if (url.pathname === '/api/profile' && request.method() === 'PUT') {
      backend.profile = request.postDataJSON() as Profile
      backend.savedProfiles.push(backend.profile)
      return json(backend.profile)
    }
    if (url.pathname === '/api/profile/resume' && request.method() === 'GET') {
      return json(backend.resumes)
    }
    if (url.pathname === '/api/profile/resume' && request.method() === 'POST') {
      // Enough of a multipart parser for one file part.
      const upload = request.postDataBuffer()?.toString('latin1') ?? ''
      const fileName = upload.match(/filename="([^"]*)"/)?.[1] ?? 'resume.pdf'
      if (!upload.includes('%PDF-')) return json({ message: 'resume must be a PDF file' }, 400)
      const created: Resume = {
        version: (backend.resumes[0]?.version ?? 0) + 1,
        latest: true,
        fileName,
        sizeBytes: 2048,
        uploadedAt: '2026-10-12T09:00:00Z',
      }
      backend.resumes = [created, ...backend.resumes.map((resume) => ({ ...resume, latest: false }))]
      return json(created, 201)
    }
    const download = url.pathname.match(/^\/api\/profile\/resume\/(\d+)\/download$/)
    if (download) {
      const resume = backend.resumes.find((candidate) => candidate.version === Number(download[1]))
      if (!resume) return json({ message: 'Not found' }, 404)
      return json({ url: `${STORAGE}/v${resume.version}/${resume.fileName}`, expiresAt: '2026-10-12T09:05:00Z' })
    }
    if (url.pathname === '/api/jobs/tailored-resumes') {
      const listed = backend.tailoredResumes
      // Tailoring "takes" one poll: whatever was pending when listed is ready the time after.
      backend.tailoredResumes = listed.map((tailored) =>
        tailored.status === 'PENDING' ? { ...tailored, status: 'READY', completedAt: new Date().toISOString() } : tailored,
      )
      return json(listed)
    }
    const tailoring = url.pathname.match(/^\/api\/jobs\/results\/([^/]+)\/tailored-resume(\/download)?$/)
    if (tailoring) {
      const jobResultId = decodeURIComponent(tailoring[1])
      const existing = backend.tailoredResumes.find((tailored) => tailored.jobResultId === jobResultId)
      if (tailoring[2]) {
        return existing?.status === 'READY'
          ? json({ url: `${STORAGE}/tailored/${jobResultId}/Ada-Lovelace-resume.pdf`, expiresAt: '2026-10-13T09:05:00Z' })
          : json({ message: 'Not found' }, 404)
      }
      if (request.method() === 'POST') {
        const latest = backend.resumes.find((resume) => resume.latest)
        if (!latest) return json({ message: 'upload your resume before tailoring it to a job' }, 409)
        const body = (request.postDataJSON() ?? {}) as {
          template?: TailoredResume['template']
          fix?: { keywords: string[]; redFlags: boolean }
        }
        const template = body.template ?? existing?.template ?? 'CLASSIC'
        let fix = existing?.fix ?? null
        if (body.fix) {
          // As the server does: fixes come from a finished review of the latest resume, and only the
          // keywords it listed can be ticked.
          fix = null
          if (body.fix.keywords.length > 0 || body.fix.redFlags) {
            const review = backend.resumeReviews.find((found) => found.jobResultId === jobResultId)
            if (review?.status !== 'READY' || review.resumeVersion !== latest.version) {
              return json({ message: 'review your latest resume against this job before fixing what the review finds' }, 409)
            }
            const keywords = (review.missingKeywords ?? []).filter((keyword) => body.fix?.keywords.includes(keyword))
            const redFlags = body.fix.redFlags ? (review.redFlags ?? []) : []
            fix = keywords.length > 0 || redFlags.length > 0 ? { keywords, redFlags } : null
          }
        }
        const sameFix = JSON.stringify(fix) === JSON.stringify(existing?.fix ?? null)
        if (existing?.status === 'READY' && existing.resumeVersion === latest.version && sameFix) {
          // Only the template changed: laid out again straight away, as the server does.
          const relaidOut = { ...existing, template }
          backend.tailoredResumes = backend.tailoredResumes.map((tailored) => (tailored === existing ? relaidOut : tailored))
          return json(relaidOut)
        }
        const pending: TailoredResume = {
          jobResultId,
          status: 'PENDING',
          resumeVersion: latest.version,
          template,
          failureReason: null,
          requestedAt: new Date().toISOString(),
          completedAt: null,
          fix,
        }
        backend.tailoredResumes = [...backend.tailoredResumes.filter((tailored) => tailored !== existing), pending]
        return json(pending, 202)
      }
      return existing ? json(existing) : json({ message: 'Not found' }, 404)
    }
    if (url.pathname === '/api/jobs/resume-reviews') {
      const listed = backend.resumeReviews
      // Reviewing "takes" one poll: whatever was pending when listed is ready the time after.
      backend.resumeReviews = listed.map((review) =>
        review.status === 'PENDING'
          ? { ...review, ...REVIEW_FINDINGS, status: 'READY', reviewedAt: new Date().toISOString() }
          : review,
      )
      return json(listed)
    }
    const reviewing = url.pathname.match(/^\/api\/jobs\/results\/([^/]+)\/resume-review$/)
    if (reviewing && request.method() === 'POST') {
      const jobResultId = decodeURIComponent(reviewing[1])
      const latest = backend.resumes.find((resume) => resume.latest)
      if (!latest) return json({ message: 'upload your resume before it can be reviewed against a job' }, 409)
      const existing = backend.resumeReviews.find((review) => review.jobResultId === jobResultId)
      if (existing?.status === 'READY' && existing.resumeVersion === latest.version) return json(existing)
      const pending: ResumeReview = {
        jobResultId,
        status: 'PENDING',
        resumeVersion: latest.version,
        resumeAtsScore: null,
        missingKeywords: null,
        redFlags: null,
        failureReason: null,
        requestedAt: new Date().toISOString(),
        reviewedAt: null,
      }
      backend.resumeReviews = [...backend.resumeReviews.filter((review) => review !== existing), pending]
      return json(pending, 202)
    }
    if (url.pathname === '/api/jobs/results') {
      return json(backend.results)
    }
    if (url.pathname === '/api/jobs/search-runs') {
      const page = Number(url.searchParams.get('page') ?? 0)
      const size = Number(url.searchParams.get('size') ?? 20)
      return json(backend.searchRuns.slice(page * size, (page + 1) * size))
    }
    const searchRun = url.pathname.match(/^\/api\/jobs\/search-runs\/([^/]+)$/)
    if (searchRun) {
      const run = backend.searchRuns.find((candidate) => candidate.runId === decodeURIComponent(searchRun[1]))
      if (!run) return json({ message: 'Not found' }, 404)
      return json({ run, companies: backend.searchRunCompanies[run.runId] ?? [] })
    }
    return json({ message: `The e2e fake backend has no answer for ${request.method()} ${url.pathname}` }, 500)
  })
}

/** Stands in for S3: serves any download link as a file to save, as the real signed links do. */
async function fakeStorage(page: Page) {
  await page.route(`${STORAGE}/**`, (route) => {
    const fileName = decodeURIComponent(new URL(route.request().url()).pathname.split('/').pop() ?? 'resume.pdf')
    return route.fulfill({
      status: 200,
      contentType: 'application/pdf',
      headers: { 'Content-Disposition': `attachment; filename="${fileName}"` },
      body: '%PDF-1.7 e2e resume',
    })
  })
}

export const test = base.extend<{ backend: Backend }>({
  // Tests change this object to set up their scenario; it starts as a user with no profile.
  backend: async ({ page }, provide) => {
    const backend: Backend = {
      profile: null,
      results: [],
      companies: ['Deliveroo', 'Dell', 'Deloitte', 'Monzo'],
      skills: ['Java', 'JavaScript', 'Spring Boot'],
      resumes: [],
      tailoredResumes: [],
      resumeReviews: [],
      searchRuns: [],
      searchRunCompanies: {},
      savedProfiles: [],
      rejectTokens: false,
      rejectPassword: false,
    }
    await fakeCognito(page, backend)
    await fakeBackend(page, backend)
    await fakeStorage(page)
    await provide(backend)
  },
})

export { expect }

/** Signs in through the real sign-in form. Leaves the app wherever sign-in sends the user. */
export async function signIn(page: Page, { from = '/login' } = {}) {
  await page.goto(from)
  await page.getByLabel('Email').fill(USER.email)
  await page.getByLabel('Password', { exact: true }).fill(USER.password)
  await page.getByRole('button', { name: 'Sign in' }).click()
}
