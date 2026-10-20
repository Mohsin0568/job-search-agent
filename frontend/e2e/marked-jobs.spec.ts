import { expect, jobResult, PROFILE, signIn, test } from './app'

test.beforeEach(({ backend }) => {
  backend.profile = PROFILE
  backend.results = [
    jobResult('j1', 'Acme Corp', { jobTitle: 'Staff Engineer' }),
    jobResult('j2', 'Acme Corp', { jobTitle: 'Platform Engineer' }),
  ]
})

test('a job marked applied leaves the dashboard and is listed under Marked jobs', async ({ page, backend }) => {
  await signIn(page)

  await page.getByRole('button', { name: 'Mark Staff Engineer as applied' }).click()

  await expect(page.getByRole('status')).toHaveText('“Staff Engineer” at Acme Corp is marked as applied.')
  await expect(page.getByRole('heading', { name: 'Staff Engineer' })).toHaveCount(0)
  await expect(page.getByRole('heading', { name: 'Platform Engineer' })).toBeVisible()
  expect(backend.results.find((result) => result.id === 'j1')?.status).toBe('APPLIED')

  await page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'Marked jobs' }).click()
  await expect(page).toHaveURL(/\/marked$/)
  const card = page.getByRole('article').filter({ hasText: 'Staff Engineer' })
  await expect(card.getByText(/^Applied on /)).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Platform Engineer' })).toHaveCount(0)
})

test('a job marked not relevant keeps the reason, and can go back to the dashboard', async ({ page, backend }) => {
  await signIn(page)

  await page.getByRole('button', { name: 'Mark Platform Engineer as not relevant' }).click()
  await page.getByLabel(/Why isn’t it relevant/).fill('Needs relocation to Leeds')
  await page.getByRole('button', { name: 'Mark not relevant' }).click()
  await expect(page.getByRole('heading', { name: 'Platform Engineer' })).toHaveCount(0)

  await page.getByRole('link', { name: 'View not-relevant jobs' }).click()
  await expect(page).toHaveURL(/\/marked\?tab=not-relevant$/)
  const card = page.getByRole('article').filter({ hasText: 'Platform Engineer' })
  await expect(card.getByText('Needs relocation to Leeds')).toBeVisible()

  await card.getByRole('button', { name: 'Move Platform Engineer back to your dashboard' }).click()
  await expect(page.getByText('Nothing marked not relevant')).toBeVisible()
  expect(backend.results.find((result) => result.id === 'j2')).toMatchObject({ status: 'ACTIVE', notRelevantReason: null })

  await page.getByRole('link', { name: 'Go to dashboard' }).click()
  await expect(page.getByRole('heading', { name: 'Platform Engineer' })).toBeVisible()
})
