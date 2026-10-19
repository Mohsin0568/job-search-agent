import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import type { JobSearchRunDetail, JobSearchRunSummary } from '../api/types'
import { SEARCH_RUNS_PAGE_SIZE } from '../api/hooks'
import { companySummary, searchRun, searchRunDetail, sourceSummary } from '../test/fixtures'
import { renderRoutes } from '../test/render'
import { API, server } from '../test/server'
import { SearchHistoryPage } from './SearchHistoryPage'

const routes = [{ path: '/history', element: <SearchHistoryPage /> }]

function runs(...pages: JobSearchRunSummary[][]) {
  return http.get(`${API}/api/jobs/search-runs`, ({ request }) =>
    HttpResponse.json(pages[Number(new URL(request.url).searchParams.get('page'))] ?? []),
  )
}

function runDetail(detail: JobSearchRunDetail) {
  return http.get(`${API}/api/jobs/search-runs/${detail.run.runId}`, () => HttpResponse.json(detail))
}

function renderPage() {
  return renderRoutes(routes, { at: '/history' })
}

describe('SearchHistoryPage', () => {
  it('shows a placeholder while the history loads', () => {
    server.use(runs([]))
    renderPage()

    expect(screen.getByLabelText('Loading your search history')).toBeInTheDocument()
  })

  it('explains that searches appear once the daily search has run', async () => {
    server.use(runs([]))
    renderPage()

    expect(await screen.findByText('No searches yet')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('lists each search with its status and totals, newest first', async () => {
    server.use(
      runs([
        searchRun({ runId: 'run-2', status: 'PARTIAL', newJobs: 5, duplicateJobs: 2, filteredOutJobs: 1, sourcesFailed: 1 }),
        searchRun({ runId: 'run-1', startedAt: '2026-10-17T02:00:00Z', finishedAt: '2026-10-17T02:00:40Z', newJobs: 0 }),
      ]),
    )
    renderPage()

    const table = await screen.findByRole('table', { name: 'Your job searches, newest first' })
    const headers = within(table).getAllByRole('columnheader').map((header) => header.textContent)
    expect(headers).toEqual(['Search', 'Status', 'Companies', 'New jobs', 'Already saved', 'Filtered out'])

    const [, newest, older] = within(table).getAllByRole('row')
    expect(within(newest).getByText('Partly completed')).toBeInTheDocument()
    expect(within(newest).getByText('1 source failed')).toBeInTheDocument()
    expect(within(newest).getByText('Took 5 min')).toBeInTheDocument()
    expect(within(newest).getAllByRole('cell').map((cell) => cell.textContent)).toEqual([
      // The last line stands in for the count columns on a phone.
      'Partly completed1 source failed5 new · 2 already saved · 1 filtered out',
      '2',
      '5',
      '2',
      '1',
    ])
    expect(within(older).getByText('Completed')).toBeInTheDocument()
    expect(within(older).getByText('Took 40 sec')).toBeInTheDocument()
  })

  it('opens a search to show what each source found for each company, in the order searched', async () => {
    const run = searchRun()
    server.use(
      runs([run]),
      runDetail(
        searchRunDetail(run, [
          companySummary({
            companyName: 'Acme Corp',
            sources: [
              sourceSummary({ source: 'Company careers portal', jobsFound: 2, newJobs: 2, duplicateJobs: 0, filteredOutJobs: 0 }),
              sourceSummary({ source: 'LinkedIn', jobsFound: 4, newJobs: 1, duplicateJobs: 1, filteredOutJobs: 2 }),
            ],
          }),
          companySummary({
            companyName: 'Globex',
            sources: [sourceSummary({ source: 'LinkedIn', status: 'FAILED', jobsFound: 0, newJobs: 0, duplicateJobs: 0, filteredOutJobs: 0 })],
          }),
        ]),
      ),
    )
    renderPage()
    const user = userEvent.setup()

    const toggle = await screen.findByRole('button', { name: /^Show what the search on .* found$/ })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    await user.click(toggle)

    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    const companies = await screen.findAllByRole('region')
    expect(companies.map((company) => company.getAttribute('aria-label'))).toEqual(['Acme Corp', 'Globex'])

    const acmeRows = within(companies[0]).getAllByRole('row').slice(1)
    expect(acmeRows.map((row) => row.textContent)).toEqual(['Company careers portal2200', 'LinkedIn4112'])
    expect(within(companies[1]).getByText('Couldn’t be searched this time')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /^Hide what the search on .* found$/ }))
    expect(screen.queryByRole('region')).not.toBeInTheDocument()
  })

  it('asks for a search’s details only once it is opened', async () => {
    let detailRequests = 0
    const run = searchRun()
    server.use(
      runs([run]),
      http.get(`${API}/api/jobs/search-runs/run-1`, () => {
        detailRequests += 1
        return HttpResponse.json(searchRunDetail(run))
      }),
    )
    renderPage()
    const user = userEvent.setup()

    const toggle = await screen.findByRole('button', { name: /^Show what the search/ })
    expect(detailRequests).toBe(0)

    await user.click(toggle)
    await screen.findByRole('region', { name: 'Acme Corp' })
    // Closing and opening again reuses what was loaded: a finished search doesn't change.
    await user.click(toggle)
    await user.click(toggle)
    await screen.findByRole('region', { name: 'Acme Corp' })
    expect(detailRequests).toBe(1)
  })

  it.each([
    ['SKIPPED', 'Your profile had no target companies to search.'],
    ['FAILED', 'This search failed before it reached any of your companies.'],
  ] as const)('explains a %s search that reached no company', async (status, explanation) => {
    const run = searchRun({ status, companiesSearched: 0, newJobs: 0, duplicateJobs: 0, filteredOutJobs: 0 })
    server.use(runs([run]), runDetail(searchRunDetail(run, [])))
    renderPage()

    await userEvent.setup().click(await screen.findByRole('button', { name: /^Show what the search/ }))

    expect(await screen.findByText(explanation)).toBeInTheDocument()
  })

  it('lets a search’s details be retried when they fail to load', async () => {
    const run = searchRun()
    server.use(
      runs([run]),
      http.get(`${API}/api/jobs/search-runs/run-1`, () => HttpResponse.json({ message: 'Service down' }, { status: 503 })),
    )
    renderPage()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /^Show what the search/ }))
    expect(await screen.findByText(/We couldn’t load this search’s details/)).toBeInTheDocument()

    server.use(runDetail(searchRunDetail(run)))
    await user.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('region', { name: 'Acme Corp' })).toBeInTheDocument()
  })

  it('loads older searches a page at a time', async () => {
    const fullPage = Array.from({ length: SEARCH_RUNS_PAGE_SIZE }, (_, i) => searchRun({ runId: `new-${i}` }))
    server.use(runs(fullPage, [searchRun({ runId: 'oldest', status: 'FAILED' })]))
    renderPage()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Show older searches' }))

    expect(await screen.findByText('Failed')).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: /^Show what the search/ })).toHaveLength(SEARCH_RUNS_PAGE_SIZE + 1)
    // A short page was the last one.
    expect(screen.queryByRole('button', { name: 'Show older searches' })).not.toBeInTheDocument()
  })

  it('offers no more pages when the first one is not full', async () => {
    server.use(runs([searchRun()]))
    renderPage()

    await screen.findByRole('table')
    expect(screen.queryByRole('button', { name: 'Show older searches' })).not.toBeInTheDocument()
  })

  it('lets the history be retried when it fails to load', async () => {
    server.use(http.get(`${API}/api/jobs/search-runs`, () => HttpResponse.json({ message: 'Service down' }, { status: 503 })))
    renderPage()

    expect(await screen.findByText('We couldn’t load your search history')).toBeInTheDocument()

    server.use(runs([searchRun()]))
    await userEvent.setup().click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('table')).toBeInTheDocument()
  })
})
