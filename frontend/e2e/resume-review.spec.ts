import { expect, jobResult, PROFILE, signIn, test } from './app'

const RESUME = { version: 1, latest: true, fileName: 'ada-cv.pdf', sizeBytes: 2048, uploadedAt: '2026-10-12T09:00:00Z' }

test.beforeEach(({ backend }) => {
  backend.profile = PROFILE
  backend.resumes = [RESUME]
  backend.results = [
    jobResult('j1', 'Acme Corp', { jobTitle: 'Staff Engineer' }),
    jobResult('j2', 'Acme Corp', { jobTitle: 'Platform Engineer', atsScore: 60 }),
  ]
})

test('a user reviews their resume against a job and reads what the recruiter found', async ({ page, backend }) => {
  await signIn(page)

  const card = page.getByRole('article').filter({ hasText: 'Staff Engineer' })
  await expect(card.getByText('Profile match 85%')).toBeVisible()
  await card.getByRole('button', { name: 'Review resume for Staff Engineer' }).click()

  await expect(card.getByRole('status')).toHaveText('Reviewing your resume for this job…')
  // Only the job that was asked for is reviewed.
  await expect(page.getByRole('button', { name: 'Review resume for Platform Engineer' })).toBeVisible()

  // The page keeps asking until the server is done.
  await expect(card.getByText('Resume ATS 72%')).toBeVisible({ timeout: 15_000 })
  expect(backend.resumeReviews.map((review) => review.jobResultId)).toEqual(['j1'])

  const summary = card.getByText('3 missing keywords · 1 red flag')
  await expect(card.getByRole('list', { name: 'Missing keywords' })).toBeHidden()
  await summary.click()
  await expect(card.getByRole('list', { name: 'Missing keywords' }).getByRole('listitem')).toHaveText([
    'Kubernetes',
    'Terraform',
    'gRPC',
  ])
  await expect(card.getByRole('list', { name: 'Red flags' })).toContainText('No measurable results')
})

test('a review made by the daily job is on the card after sign-in, and offered again after a new upload', async ({
  page,
  backend,
}) => {
  backend.resumeReviews = [
    {
      jobResultId: 'j2',
      status: 'READY',
      resumeVersion: 1,
      resumeAtsScore: 91,
      missingKeywords: [],
      redFlags: [],
      failureReason: null,
      requestedAt: '2026-10-15T02:00:00Z',
      reviewedAt: '2026-10-15T02:00:40Z',
    },
  ]
  await signIn(page)

  const card = page.getByRole('article').filter({ hasText: 'Platform Engineer' })
  await expect(card.getByText('Resume ATS 91%')).toBeVisible()
  await expect(card.getByText('No missing keywords · No red flags')).toBeVisible()

  backend.resumes = [{ ...RESUME, version: 2, uploadedAt: '2026-10-16T09:00:00Z' }, { ...RESUME, latest: false }]
  await page.reload()

  await expect(card.getByText('Reviewed against an earlier version of your resume.')).toBeVisible()
  await card.getByRole('button', { name: 'Review again for Platform Engineer' }).click()
  await expect(card.getByText('Resume ATS 72%')).toBeVisible({ timeout: 15_000 })
  expect(backend.resumeReviews.map((review) => review.resumeVersion)).toEqual([2])
})
