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

export type Backend = {
  /** null means the user hasn't set up a profile yet (the API answers 404). */
  profile: Profile | null
  results: Array<{ id: string; userId: string; jobRunDateTime: string; companyName: string; job: Job }>
  companies: string[]
  skills: string[]
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
    if (url.pathname === '/api/jobs/results') {
      return json(backend.results)
    }
    return json({ message: `The e2e fake backend has no answer for ${request.method()} ${url.pathname}` }, 500)
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
      savedProfiles: [],
      rejectTokens: false,
      rejectPassword: false,
    }
    await fakeCognito(page, backend)
    await fakeBackend(page, backend)
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
