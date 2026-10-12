import { expect, PROFILE, signIn, test } from './app'

const PDF = { name: 'jane-cv.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.7 a resume') }

test('a new user uploads a resume while setting up their profile', async ({ page, backend }) => {
  await signIn(page)
  await expect(page).toHaveURL('/onboarding')

  const resume = page.getByRole('region', { name: 'Your resume' })
  await expect(resume.getByText('You haven’t uploaded a resume yet.')).toBeVisible()
  await resume.getByLabel('Upload your resume').setInputFiles(PDF)

  await expect(resume.getByRole('status')).toHaveText('Your resume has been uploaded as version 1.')
  await expect(resume.getByText('jane-cv.pdf')).toBeVisible()
  expect(backend.resumes.map((uploaded) => uploaded.fileName)).toEqual(['jane-cv.pdf'])

  // The upload is saved on its own: the profile still has to be filled in.
  await expect(page).toHaveURL('/onboarding')
  expect(backend.savedProfiles).toEqual([])
})

test('a user replaces their resume and downloads an earlier version', async ({ page, backend }) => {
  backend.profile = PROFILE
  backend.resumes = [
    { version: 1, latest: true, fileName: 'old-cv.pdf', sizeBytes: 1024, uploadedAt: '2026-09-01T09:00:00Z' },
  ]
  await signIn(page)
  await page.goto('/profile')

  const resume = page.getByRole('region', { name: 'Your resume' })
  await expect(resume.getByText('old-cv.pdf')).toBeVisible()
  await resume.getByLabel('Upload a new version').setInputFiles({ ...PDF, name: 'new-cv.pdf' })

  await expect(resume.getByRole('status')).toHaveText('Your resume has been uploaded as version 2.')
  await expect(resume.getByRole('listitem')).toHaveText([/old-cv\.pdf/])

  const downloading = page.waitForEvent('download')
  await resume.getByRole('button', { name: 'Download version 1' }).click()
  expect((await downloading).suggestedFilename()).toBe('old-cv.pdf')
  // Saving the file leaves the page where it was.
  await expect(page).toHaveURL('/profile')
})

test('a file that is not a PDF is turned away', async ({ page, backend }) => {
  backend.profile = PROFILE
  await signIn(page)
  await page.goto('/profile')

  const resume = page.getByRole('region', { name: 'Your resume' })
  await resume
    .getByLabel('Upload your resume')
    .setInputFiles({ name: 'cv.docx', mimeType: 'application/msword', buffer: Buffer.from('a word document') })

  await expect(resume.getByRole('alert')).toHaveText('Your resume must be a PDF file.')
  expect(backend.resumes).toEqual([])
})
