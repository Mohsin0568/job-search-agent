import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import type { JobSearchResult } from '../api/types'
import { result, resumeReview } from '../test/fixtures'
import { renderRoutes } from '../test/render'
import { API, server } from '../test/server'
import { MarkedJobsPage } from './MarkedJobsPage'

const routes = [
  { path: '/marked', element: <MarkedJobsPage /> },
  { path: '/', element: <p>Dashboard</p> },
]

function applied(id: string, title: string, overrides: Partial<JobSearchResult> = {}): JobSearchResult {
  return result(id, 'Acme Corp', { jobTitle: title }, { status: 'APPLIED', statusChangedAt: '2026-10-20T09:00:00Z', ...overrides })
}

// A backend that keeps what it's told, listing each status a page at a time as the real one does.
function backend(initial: JobSearchResult[]) {
  const state = { jobs: initial, queries: [] as string[] }
  server.use(
    http.get(`${API}/api/jobs/results`, ({ request }) => {
      const params = new URL(request.url).searchParams
      state.queries.push(params.toString())
      const page = Number(params.get('page'))
      const size = Number(params.get('size'))
      const matching = state.jobs.filter((job) => job.status === params.get('status'))
      return HttpResponse.json(matching.slice(page * size, (page + 1) * size))
    }),
    http.put(`${API}/api/jobs/results/:id/status`, async ({ request, params }) => {
      const { status, reason } = (await request.json()) as { status: JobSearchResult['status']; reason: string | null }
      state.jobs = state.jobs.map((job) =>
        job.id === params.id
          ? { ...job, status, notRelevantReason: status === 'NOT_RELEVANT' ? reason : null, statusChangedAt: '2026-10-21T09:00:00Z' }
          : job,
      )
      return HttpResponse.json(state.jobs.find((job) => job.id === params.id))
    }),
  )
  return state
}

beforeEach(() => {
  server.use(http.get(`${API}/api/jobs/resume-reviews`, () => HttpResponse.json([])))
})

describe('MarkedJobsPage', () => {
  it('lists the jobs the user applied for, with their company and when they applied', async () => {
    const state = backend([applied('r-1', 'Staff Engineer'), result('r-2', 'Acme Corp', { jobTitle: 'On the dashboard' })])
    renderRoutes(routes, { at: '/marked' })

    expect(screen.getByRole('heading', { name: 'Marked jobs' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Applied' })).toHaveAttribute('aria-current', 'page')
    const card = (await screen.findByRole('heading', { name: 'Staff Engineer' })).closest('article')!
    expect(within(card).getByText('Acme Corp')).toBeInTheDocument()
    expect(within(card).getByText(/^Applied on .*2026$/)).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'On the dashboard' })).not.toBeInTheDocument()
    expect(state.queries).toEqual(['status=APPLIED&page=0&size=20'])
  })

  it('lists the jobs marked not relevant, with the user’s reason', async () => {
    backend([
      applied('r-1', 'Staff Engineer', { status: 'NOT_RELEVANT', notRelevantReason: 'Too senior' }),
      applied('r-2', 'Applied one'),
    ])
    renderRoutes(routes, { at: '/marked?tab=not-relevant' })

    const card = (await screen.findByRole('heading', { name: 'Staff Engineer' })).closest('article')!
    expect(screen.getByRole('link', { name: 'Not relevant' })).toHaveAttribute('aria-current', 'page')
    expect(within(card).getByText(/^Marked not relevant on/)).toBeInTheDocument()
    expect(within(card).getByText('Too senior')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Applied one' })).not.toBeInTheDocument()
  })

  it('switches between the tabs', async () => {
    backend([applied('r-1', 'Staff Engineer'), applied('r-2', 'Not for me', { status: 'NOT_RELEVANT' })])
    renderRoutes(routes, { at: '/marked' })

    await screen.findByRole('heading', { name: 'Staff Engineer' })
    await userEvent.click(screen.getByRole('link', { name: 'Not relevant' }))
    expect(await screen.findByRole('heading', { name: 'Not for me' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Staff Engineer' })).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('link', { name: 'Applied' }))
    expect(await screen.findByRole('heading', { name: 'Staff Engineer' })).toBeInTheDocument()
  })

  it('explains each empty tab', async () => {
    backend([])
    renderRoutes(routes, { at: '/marked' })

    expect(await screen.findByText('No applications yet')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('link', { name: 'Not relevant' }))
    expect(await screen.findByText('Nothing marked not relevant')).toBeInTheDocument()
  })

  it('moves a job back to the dashboard, and can undo it', async () => {
    const state = backend([applied('r-1', 'Staff Engineer'), applied('r-2', 'Platform Engineer')])
    renderRoutes(routes, { at: '/marked' })

    await userEvent.click(await screen.findByRole('button', { name: 'Move Staff Engineer back to your dashboard' }))

    expect(await screen.findByRole('status')).toHaveTextContent('“Staff Engineer” at Acme Corp is back on your dashboard.')
    expect(screen.queryByRole('heading', { name: 'Staff Engineer' })).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Platform Engineer' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Go to dashboard' })).toHaveAttribute('href', '/')

    await userEvent.click(screen.getByRole('button', { name: 'Undo' }))
    expect(await screen.findByRole('heading', { name: 'Staff Engineer' })).toBeInTheDocument()
    expect(state.jobs[0].status).toBe('APPLIED')
  })

  it('undoing a move off "not relevant" puts the reason back too', async () => {
    const state = backend([applied('r-1', 'Staff Engineer', { status: 'NOT_RELEVANT', notRelevantReason: 'Too senior' })])
    renderRoutes(routes, { at: '/marked?tab=not-relevant' })

    await userEvent.click(await screen.findByRole('button', { name: 'Mark Staff Engineer as applied' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Undo' }))

    expect(await screen.findByText('Too senior')).toBeInTheDocument()
    expect(state.jobs[0]).toMatchObject({ status: 'NOT_RELEVANT', notRelevantReason: 'Too senior' })
  })

  it('edits a not-relevant job’s reason in place, offering the review’s findings', async () => {
    server.use(http.get(`${API}/api/jobs/resume-reviews`, () => HttpResponse.json([resumeReview({ jobResultId: 'r-1' })])))
    backend([applied('r-1', 'Staff Engineer', { status: 'NOT_RELEVANT', notRelevantReason: 'Too senior' })])
    renderRoutes(routes, { at: '/marked?tab=not-relevant' })

    await userEvent.click(await screen.findByRole('button', { name: 'Edit why Staff Engineer isn’t relevant' }))
    await userEvent.click(screen.getByRole('button', { name: 'Add “No measurable results” to the reason' }))
    await userEvent.click(screen.getByRole('button', { name: 'Save reason' }))

    expect(await screen.findByText('Too senior; No measurable results')).toBeInTheDocument()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('loads older jobs a page at a time', async () => {
    const jobs = Array.from({ length: 25 }, (_, i) => applied(`r-${i}`, `Job ${i}`))
    backend(jobs)
    renderRoutes(routes, { at: '/marked' })

    await screen.findByRole('heading', { name: 'Job 19' })
    expect(screen.queryByRole('heading', { name: 'Job 20' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Show more' }))

    expect(await screen.findByRole('heading', { name: 'Job 24' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Show more' })).not.toBeInTheDocument()
  })

  it('reports a failure to load and loads the jobs on retry', async () => {
    let fail = true
    server.use(
      http.get(`${API}/api/jobs/results`, () =>
        fail ? HttpResponse.json({ message: 'Service unavailable' }, { status: 503 }) : HttpResponse.json([applied('r-1', 'Staff Engineer')]),
      ),
    )
    renderRoutes(routes, { at: '/marked' })

    expect(await screen.findByText('We couldn’t load these jobs')).toBeInTheDocument()
    fail = false
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('heading', { name: 'Staff Engineer' })).toBeInTheDocument()
  })
})
