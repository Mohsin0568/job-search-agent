import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import type { JobSearchResult } from '../api/types'
import { profile, result, resumeVersion, tailoredResume } from '../test/fixtures'
import { renderRoutes } from '../test/render'
import { API, server } from '../test/server'
import { DashboardPage } from './DashboardPage'

const routes = [{ path: '/', element: <DashboardPage /> }]

function jobResults(results: JobSearchResult[]) {
  return http.get(`${API}/api/jobs/results`, () => HttpResponse.json(results))
}

beforeEach(() => {
  server.use(
    http.get(`${API}/api/profile`, () => HttpResponse.json(profile())),
    // By default the user has a resume and has tailored nothing yet.
    http.get(`${API}/api/profile/resume`, () => HttpResponse.json([resumeVersion({ version: 3 })])),
    http.get(`${API}/api/jobs/tailored-resumes`, () => HttpResponse.json([])),
  )
})

describe('DashboardPage', () => {
  it('greets the user and names the role being searched for', async () => {
    server.use(jobResults([]))
    renderRoutes(routes)

    expect(screen.getByRole('heading', { name: 'Welcome, Ada Lovelace' })).toBeInTheDocument()
    expect(await screen.findByText('Senior Java Developer')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Edit search preferences' })).toHaveAttribute('href', '/profile')
  })

  it('shows a placeholder while results load', () => {
    server.use(jobResults([]))
    renderRoutes(routes)

    expect(screen.getByLabelText('Loading job matches')).toBeInTheDocument()
  })

  it('explains that matches arrive from the daily search when there are none yet', async () => {
    server.use(jobResults([]))
    renderRoutes(routes)

    expect(await screen.findByText('No job matches yet')).toBeInTheDocument()
    expect(screen.getByText(/We search your target companies every day/)).toBeInTheDocument()
    expect(screen.queryByText(/Updated/)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /search/i })).not.toBeInTheDocument()
  })

  it('asks for the newest 100 results', async () => {
    let query = ''
    server.use(
      http.get(`${API}/api/jobs/results`, ({ request }) => {
        query = new URL(request.url).search
        return HttpResponse.json([])
      }),
    )
    renderRoutes(routes)

    await screen.findByText('No job matches yet')
    expect(query).toBe('?page=0&size=100')
  })

  it('groups jobs by company in the user’s preferred order, best match first', async () => {
    // The profile prefers Acme Corp, then Globex.
    server.use(
      jobResults([
        result('1', 'Initech', { jobTitle: 'Platform Engineer' }),
        result('2', 'globex', { jobTitle: 'Data Engineer' }),
        result('3', 'Acme Corp', { jobTitle: 'Junior Developer', atsScore: 55 }),
        result('4', 'Acme Corp', { jobTitle: 'Staff Engineer', atsScore: 92 }),
      ]),
    )
    renderRoutes(routes)

    const sections = await screen.findAllByRole('region')
    expect(sections.map((section) => within(section).getByRole('heading', { level: 2 }).textContent)).toEqual([
      'Acme Corp2 jobs',
      'globex1 job',
      'Initech1 job',
    ])
    expect(
      within(sections[0])
        .getAllByRole('heading', { level: 3 })
        .map((heading) => heading.textContent),
    ).toEqual(['Staff Engineer', 'Junior Developer'])
    expect(screen.getByText(/Updated/)).toBeInTheDocument()
    expect(screen.queryByText(/No recent openings found/)).not.toBeInTheDocument()
  })

  it('names the preferred companies that had no openings', async () => {
    server.use(jobResults([result('1', 'Acme Corp')]))
    renderRoutes(routes)

    expect(await screen.findByText('No recent openings found at Globex.')).toBeInTheDocument()
  })

  it('reports a failure to load and loads the results on retry', async () => {
    server.use(
      http.get(`${API}/api/jobs/results`, () => HttpResponse.json({ message: 'Search service down' }, { status: 503 })),
    )
    renderRoutes(routes)

    expect(await screen.findByText('We couldn’t load your job matches')).toBeInTheDocument()
    expect(screen.getByText('Search service down')).toBeInTheDocument()

    server.use(jobResults([result('1', 'Acme Corp', { jobTitle: 'Staff Engineer' })]))
    await userEvent.setup().click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('heading', { name: 'Staff Engineer' })).toBeInTheDocument()
    expect(screen.queryByText('We couldn’t load your job matches')).not.toBeInTheDocument()
  })

  it('offers on each job to tailor the resume, or to download one already tailored', async () => {
    server.use(
      jobResults([
        result('r-1', 'Acme Corp', { jobTitle: 'Staff Engineer' }),
        result('r-2', 'Acme Corp', { jobTitle: 'Platform Engineer', atsScore: 60 }),
      ]),
      http.get(`${API}/api/jobs/tailored-resumes`, () =>
        HttpResponse.json([tailoredResume({ jobResultId: 'r-1', status: 'READY', resumeVersion: 3 })]),
      ),
    )
    renderRoutes(routes)

    expect(await screen.findByRole('button', { name: 'Download tailored resume for Staff Engineer' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Tailor resume for Platform Engineer' })).toBeInTheDocument()
  })

  it('points a user with no resume at uploading one', async () => {
    server.use(
      jobResults([result('r-1', 'Acme Corp')]),
      http.get(`${API}/api/profile/resume`, () => HttpResponse.json([])),
    )
    renderRoutes(routes)

    expect(await screen.findByRole('link', { name: 'Upload your resume' })).toHaveAttribute('href', '/profile')
    expect(screen.queryByRole('button', { name: /Tailor resume/ })).not.toBeInTheDocument()
  })

  it('still shows the jobs when tailoring cannot be loaded', async () => {
    server.use(
      jobResults([result('r-1', 'Acme Corp', { jobTitle: 'Staff Engineer' })]),
      http.get(`${API}/api/jobs/tailored-resumes`, () => HttpResponse.json({ message: 'down' }, { status: 503 })),
    )
    renderRoutes(routes)

    expect(await screen.findByRole('heading', { name: 'Staff Engineer' })).toBeInTheDocument()
    expect(await screen.findByText('Resume tailoring is unavailable right now.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Tailor resume/ })).not.toBeInTheDocument()
  })
})
