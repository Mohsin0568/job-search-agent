import { expect, PROFILE, signIn, test, type SearchRun } from './app'

const RUN: SearchRun = {
  runId: 'run-2',
  runDate: '2026-10-18',
  startedAt: '2026-10-18T02:00:00Z',
  finishedAt: '2026-10-18T02:05:00Z',
  status: 'PARTIAL',
  companiesSearched: 2,
  newJobs: 3,
  duplicateJobs: 1,
  filteredOutJobs: 2,
  sourcesFailed: 1,
}

test.beforeEach(({ backend }) => {
  backend.profile = PROFILE
})

test('the search history lists each daily search, and opens one to show each company by source', async ({
  page,
  backend,
}) => {
  backend.searchRuns = [
    RUN,
    { ...RUN, runId: 'run-1', runDate: '2026-10-17', startedAt: '2026-10-17T02:00:00Z', finishedAt: '2026-10-17T02:04:00Z', status: 'SUCCEEDED', sourcesFailed: 0 },
  ]
  backend.searchRunCompanies = {
    'run-2': [
      {
        companyName: 'Acme Corp',
        searchedAt: '2026-10-18T02:01:00Z',
        newJobs: 3,
        duplicateJobs: 1,
        filteredOutJobs: 2,
        sourcesFailed: 0,
        sources: [
          { source: 'Company careers portal', status: 'SCANNED', jobsFound: 2, newJobs: 2, duplicateJobs: 0, filteredOutJobs: 0 },
          { source: 'LinkedIn', status: 'SCANNED', jobsFound: 4, newJobs: 1, duplicateJobs: 1, filteredOutJobs: 2 },
        ],
      },
      {
        companyName: 'Globex',
        searchedAt: '2026-10-18T02:03:00Z',
        newJobs: 0,
        duplicateJobs: 0,
        filteredOutJobs: 0,
        sourcesFailed: 1,
        sources: [
          { source: 'Company careers portal', status: 'SCANNED', jobsFound: 0, newJobs: 0, duplicateJobs: 0, filteredOutJobs: 0 },
          { source: 'LinkedIn', status: 'FAILED', jobsFound: 0, newJobs: 0, duplicateJobs: 0, filteredOutJobs: 0 },
        ],
      },
    ],
  }
  await signIn(page)

  await page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'Search history' }).click()
  await expect(page).toHaveURL(/\/history$/)
  await expect(page.getByRole('heading', { name: 'Search history' })).toBeVisible()

  const table = page.getByRole('table', { name: 'Your job searches, newest first' })
  const toggles = table.getByRole('button', { name: /^Show what the search on/ })
  await expect(toggles).toHaveCount(2)
  await expect(table.getByText('Partly completed')).toBeVisible()
  await expect(table.getByText('1 source failed')).toBeVisible()

  await toggles.first().click()

  const acme = page.getByRole('region', { name: 'Acme Corp' })
  await expect(acme.getByRole('row', { name: /LinkedIn/ })).toHaveText(/LinkedIn\s*4\s*1\s*1\s*2/)
  await expect(acme.getByRole('row', { name: /Company careers portal/ })).toHaveText(/Company careers portal\s*2\s*2\s*0\s*0/)
  const globex = page.getByRole('region', { name: 'Globex' })
  await expect(globex.getByText('Couldn’t be searched this time')).toBeVisible()

  await page.getByRole('button', { name: /^Hide what the search on/ }).click()
  await expect(acme).toBeHidden()
})

test('the search history explains that it fills in as daily searches run', async ({ page }) => {
  await signIn(page, { from: '/history' })

  await expect(page.getByText('No searches yet')).toBeVisible()
})
