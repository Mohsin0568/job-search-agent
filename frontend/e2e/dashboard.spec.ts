import { expect, jobResult, PROFILE, signIn, test } from './app'

test.beforeEach(({ backend }) => {
  backend.profile = PROFILE
})

test('job matches are grouped by company in the preferred order, best match first', async ({ page, backend }) => {
  // PROFILE prefers Acme Corp, then Globex.
  backend.results = [
    jobResult('j1', 'Initech', { jobTitle: 'Platform Engineer' }),
    jobResult('j2', 'Acme Corp', { jobTitle: 'Junior Developer', atsScore: 55 }),
    jobResult('j3', 'Acme Corp', { jobTitle: 'Staff Engineer', atsScore: 92 }),
  ]
  await signIn(page)

  await expect(page.getByRole('heading', { level: 2 })).toHaveText([/^Acme Corp\s*2 jobs$/, /^Initech\s*1 job$/])
  const acme = page.getByRole('region', { name: /Acme Corp/ })
  await expect(acme.getByRole('heading', { level: 3 })).toHaveText(['Staff Engineer', 'Junior Developer'])
  await expect(acme.getByText('92% match')).toBeVisible()
  await expect(page.getByText('No recent openings found at Globex.')).toBeVisible()

  const posting = acme.getByRole('link', { name: /View posting.*Staff Engineer/ })
  await expect(posting).toHaveAttribute('href', 'https://jobs.example.com/j3')
  await expect(posting).toHaveAttribute('target', '_blank')
})

test('a scraped javascript: link is never rendered as a link', async ({ page, backend }) => {
  backend.results = [jobResult('j1', 'Acme Corp', { jobTitle: 'Staff Engineer', url: 'javascript:alert(document.domain)' })]
  await signIn(page)

  await expect(page.getByRole('heading', { name: 'Staff Engineer' })).toBeVisible()
  await expect(page.getByText('No link available')).toBeVisible()
  await expect(page.locator('a[href^="javascript"]')).toHaveCount(0)
})

test('a failure to load matches can be retried', async ({ page, backend }) => {
  await signIn(page)
  await expect(page.getByText('No job matches yet')).toBeVisible()

  await page.route('**/api/jobs/results?*', (route) =>
    route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ message: 'Search service down' }) }),
  )
  await page.reload()
  await expect(page.getByText('Search service down')).toBeVisible({ timeout: 15_000 })

  await page.unroute('**/api/jobs/results?*')
  backend.results = [jobResult('j1', 'Acme Corp', { jobTitle: 'Staff Engineer' })]
  await page.getByRole('button', { name: 'Try again' }).click()

  await expect(page.getByRole('heading', { name: 'Staff Engineer' })).toBeVisible()
})

test('search preferences can be changed from the dashboard', async ({ page, backend }) => {
  await signIn(page)
  await page.getByRole('link', { name: 'Edit search preferences' }).click()
  await expect(page).toHaveURL('/profile')
  await expect(page.getByLabel(/Role you/)).toHaveValue(PROFILE.desiredRole)

  await page.getByRole('button', { name: 'Remove Globex' }).click()
  await page.getByLabel(/Role you/).fill('Staff Engineer')
  await page.getByRole('button', { name: 'Save changes' }).click()

  await expect(page.getByText('Your preferences have been saved.')).toBeVisible()
  expect(backend.savedProfiles).toEqual([{ ...PROFILE, desiredRole: 'Staff Engineer', companyPreferences: ['Acme Corp'] }])

  // Editing again hides the confirmation, so it can't be mistaken for the new edit being saved.
  await page.getByLabel(/Role you/).fill('Principal Engineer')
  await expect(page.getByText('Your preferences have been saved.')).toBeHidden()

  await page.getByRole('link', { name: 'Dashboard' }).click()
  await expect(page.getByText('Latest matches for Staff Engineer')).toBeVisible()
})
