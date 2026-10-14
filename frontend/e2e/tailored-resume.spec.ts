import { expect, jobResult, PROFILE, signIn, test } from './app'

const RESUME = { version: 1, latest: true, fileName: 'ada-cv.pdf', sizeBytes: 2048, uploadedAt: '2026-10-12T09:00:00Z' }

test.beforeEach(({ backend }) => {
  backend.profile = PROFILE
  backend.results = [
    jobResult('j1', 'Acme Corp', { jobTitle: 'Staff Engineer' }),
    jobResult('j2', 'Acme Corp', { jobTitle: 'Platform Engineer', atsScore: 60 }),
  ]
})

test('a user tailors their resume to a job and downloads it', async ({ page, backend }) => {
  backend.resumes = [RESUME]
  await signIn(page)

  await page.getByRole('button', { name: 'Tailor resume for Staff Engineer' }).click()

  const card = page.getByRole('article').filter({ hasText: 'Staff Engineer' })
  await expect(card.getByRole('status')).toHaveText('Tailoring your resume to this job…')
  // Only the job that was asked for is tailored.
  await expect(page.getByRole('button', { name: 'Tailor resume for Platform Engineer' })).toBeVisible()

  // The page keeps asking until the server is done.
  const download = card.getByRole('button', { name: 'Download tailored resume for Staff Engineer' })
  await expect(download).toBeVisible({ timeout: 15_000 })
  expect(backend.tailoredResumes.map((tailored) => tailored.jobResultId)).toEqual(['j1'])

  const downloading = page.waitForEvent('download')
  await download.click()
  expect((await downloading).suggestedFilename()).toBe('Ada-Lovelace-resume.pdf')
  await expect(page).toHaveURL('/')
})

test('a tailored resume is still there after a reload', async ({ page, backend }) => {
  backend.resumes = [RESUME]
  backend.tailoredResumes = [
    { jobResultId: 'j2', status: 'READY', resumeVersion: 1, failureReason: null, requestedAt: '2026-10-13T09:00:00Z', completedAt: '2026-10-13T09:00:40Z' },
  ]
  await signIn(page)

  await expect(page.getByRole('button', { name: 'Download tailored resume for Platform Engineer' })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('button', { name: 'Download tailored resume for Platform Engineer' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Tailor resume for Staff Engineer' })).toBeVisible()
})

test('a user without a resume is sent to upload one first', async ({ page }) => {
  await signIn(page)

  const card = page.getByRole('article').filter({ hasText: 'Staff Engineer' })
  await expect(card.getByText('to tailor it to this job.')).toBeVisible()
  await expect(page.getByRole('button', { name: /Tailor resume/ })).toHaveCount(0)

  await card.getByRole('link', { name: 'Upload your resume' }).click()
  await expect(page).toHaveURL('/profile')
  await expect(page.getByRole('region', { name: 'Your resume' })).toBeVisible()
})
