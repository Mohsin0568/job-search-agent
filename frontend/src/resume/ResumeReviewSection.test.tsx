import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useResumeReviews } from '../api/hooks'
import type { ResumeReview } from '../api/types'
import { resumeReview } from '../test/fixtures'
import { renderRoutes } from '../test/render'
import { API, server } from '../test/server'
import { ResumeReviewSection } from './ResumeReviewSection'

// Poll quickly, so a test that waits for a review to finish doesn't wait seconds.
vi.mock('./review', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./review')>()),
  REVIEW_POLL_INTERVAL_MS: 20,
}))

const JOB = 'r-1'
const now = () => new Date().toISOString()

/** The section fed from the review list, the way the dashboard feeds it. */
function Harness({ latestResumeVersion }: { latestResumeVersion: number | null }) {
  const { data } = useResumeReviews()
  if (!data) return null
  return (
    <ResumeReviewSection
      jobResultId={JOB}
      jobTitle="Staff Engineer"
      review={data.find((review) => review.jobResultId === JOB)}
      latestResumeVersion={latestResumeVersion}
    />
  )
}

function renderSection(latestResumeVersion: number | null = 3) {
  const view = renderRoutes([{ path: '/', element: <Harness latestResumeVersion={latestResumeVersion} /> }])
  return { user: userEvent.setup(), ...view }
}

/** What the server's list holds; tests change it to play the server finishing its work. */
let onServer: ResumeReview[]

beforeEach(() => {
  onServer = []
  server.use(http.get(`${API}/api/jobs/resume-reviews`, () => HttpResponse.json(onServer)))
})

describe('ResumeReviewSection', () => {
  it('reviews the resume on request, waits while the server works, then shows what it found', async () => {
    server.use(
      http.post(`${API}/api/jobs/results/${JOB}/resume-review`, () => {
        onServer = [resumeReview({ status: 'PENDING', resumeAtsScore: null, missingKeywords: null, redFlags: null, requestedAt: now(), reviewedAt: null })]
        return HttpResponse.json(onServer[0], { status: 202 })
      }),
    )
    const { user } = renderSection()

    await user.click(await screen.findByRole('button', { name: 'Review resume for Staff Engineer' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Reviewing your resume for this job…')
    expect(screen.queryByRole('button')).not.toBeInTheDocument()

    // The server finishes; the next poll picks it up.
    onServer = [resumeReview()]

    expect(await screen.findByText('2 missing keywords · 1 red flag')).toBeInTheDocument()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('keeps the findings collapsed until the summary is opened', async () => {
    onServer = [resumeReview()]
    const { user } = renderSection()

    const summary = await screen.findByText('2 missing keywords · 1 red flag')
    const details = summary.closest('details')
    expect(details).not.toHaveAttribute('open')

    await user.click(summary)

    expect(details).toHaveAttribute('open')
    const keywords = within(screen.getByRole('list', { name: 'Missing keywords' })).getAllByRole('listitem')
    expect(keywords.map((keyword) => keyword.textContent)).toEqual(['Kubernetes', 'Terraform'])
    expect(within(screen.getByRole('list', { name: 'Red flags' })).getByRole('listitem')).toHaveTextContent(
      'No measurable results - Bullets list duties, not outcomes.',
    )
  })

  it('says so when the review found nothing', async () => {
    onServer = [resumeReview({ missingKeywords: [], redFlags: [] })]
    renderSection()

    expect(await screen.findByText('No missing keywords · No red flags')).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('shows why a review failed and offers to try again', async () => {
    onServer = [
      resumeReview({ status: 'FAILED', resumeAtsScore: null, missingKeywords: null, redFlags: null, failureReason: 'we couldn’t read the job posting' }),
    ]
    server.use(
      http.post(`${API}/api/jobs/results/${JOB}/resume-review`, () =>
        HttpResponse.json(resumeReview({ status: 'PENDING', requestedAt: now(), reviewedAt: null }), { status: 202 }),
      ),
    )
    const { user } = renderSection()

    expect(await screen.findByText('We couldn’t read the job posting.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Try the review again for Staff Engineer' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Reviewing your resume for this job…')
  })

  it('offers to review again after a newer resume was uploaded, still showing the old findings', async () => {
    onServer = [resumeReview({ resumeVersion: 2 })]
    renderSection(3)

    expect(await screen.findByText('Reviewed against an earlier version of your resume.')).toBeInTheDocument()
    expect(screen.getByText('2 missing keywords · 1 red flag')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Review again for Staff Engineer' })).toBeInTheDocument()
  })

  it('offers to try again when a review has been pending for too long', async () => {
    onServer = [resumeReview({ status: 'PENDING', requestedAt: '2020-01-01T00:00:00Z', reviewedAt: null })]
    renderSection()

    expect(await screen.findByText('The review is taking longer than it should.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Try the review again for Staff Engineer' })).toBeInTheDocument()
  })

  it('shows nothing when the user has no resume', async () => {
    let listed = false
    server.use(
      http.get(`${API}/api/jobs/resume-reviews`, () => {
        listed = true
        return HttpResponse.json([resumeReview()])
      }),
    )
    renderSection(null)

    await waitFor(() => expect(listed).toBe(true))
    // Give the list a moment to arrive and render.
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(document.body).toHaveTextContent(/^$/)
  })

  it('says why a review could not be started', async () => {
    server.use(
      http.post(`${API}/api/jobs/results/${JOB}/resume-review`, () =>
        HttpResponse.json({ message: 'upload your resume before it can be reviewed against a job' }, { status: 409 }),
      ),
    )
    const { user } = renderSection()

    await user.click(await screen.findByRole('button', { name: 'Review resume for Staff Engineer' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Upload your resume before it can be reviewed against a job',
    )
  })
})
