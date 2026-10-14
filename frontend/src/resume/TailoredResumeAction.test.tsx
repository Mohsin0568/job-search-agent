import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useTailoredResumes } from '../api/hooks'
import type { ResumeTemplate, TailoredResume } from '../api/types'
import { tailoredResume } from '../test/fixtures'
import { renderRoutes } from '../test/render'
import { API, server } from '../test/server'
import { startDownload } from './download'
import { TailoredResumeAction } from './TailoredResumeAction'

vi.mock('./download', () => ({ startDownload: vi.fn() }))
// Poll quickly, so a test that waits for tailoring to finish doesn't wait seconds.
vi.mock('./tailoring', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./tailoring')>()),
  TAILORING_POLL_INTERVAL_MS: 20,
}))

const JOB = 'r-1'
const now = () => new Date().toISOString()

/** The action fed from the tailored-resume list, the way the dashboard feeds it. */
function Harness({
  latestResumeVersion,
  defaultTemplate,
}: {
  latestResumeVersion: number | null
  defaultTemplate: ResumeTemplate
}) {
  const { data } = useTailoredResumes()
  if (!data) return null
  return (
    <TailoredResumeAction
      jobResultId={JOB}
      jobTitle="Staff Engineer"
      tailored={data.find((tailored) => tailored.jobResultId === JOB)}
      latestResumeVersion={latestResumeVersion}
      defaultTemplate={defaultTemplate}
    />
  )
}

function renderAction(latestResumeVersion: number | null = 3, defaultTemplate: ResumeTemplate = 'CLASSIC') {
  renderRoutes([
    { path: '/', element: <Harness latestResumeVersion={latestResumeVersion} defaultTemplate={defaultTemplate} /> },
    { path: '/profile', element: <p>Profile page</p> },
  ])
  return userEvent.setup()
}

/** What the server's list holds; tests change it to play the server finishing its work. */
let onServer: TailoredResume[]

beforeEach(() => {
  vi.mocked(startDownload).mockReset()
  onServer = []
  server.use(http.get(`${API}/api/jobs/tailored-resumes`, () => HttpResponse.json(onServer)))
})

describe('TailoredResumeAction', () => {
  it('tailors the resume on request, waits while the server works, then offers the download', async () => {
    server.use(
      http.post(`${API}/api/jobs/results/${JOB}/tailored-resume`, () => {
        onServer = [tailoredResume({ status: 'PENDING', requestedAt: now(), completedAt: null })]
        return HttpResponse.json(onServer[0], { status: 202 })
      }),
    )
    const user = renderAction()

    await user.click(await screen.findByRole('button', { name: 'Tailor resume for Staff Engineer' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Tailoring your resume to this job…')
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument()

    // The server finishes; the next poll picks it up.
    onServer = [tailoredResume({ status: 'READY' })]

    expect(await screen.findByRole('button', { name: 'Download tailored resume for Staff Engineer' })).toBeEnabled()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('tailors in the template the user picks', async () => {
    const bodies: unknown[] = []
    server.use(
      http.post(`${API}/api/jobs/results/${JOB}/tailored-resume`, async ({ request }) => {
        bodies.push(await request.json())
        onServer = [tailoredResume({ status: 'PENDING', template: 'MODERN', requestedAt: now(), completedAt: null })]
        return HttpResponse.json(onServer[0], { status: 202 })
      }),
    )
    const user = renderAction()

    const picker = await screen.findByRole('combobox', { name: 'Resume template for Staff Engineer' })
    expect(picker).toHaveValue('CLASSIC')
    expect(screen.getAllByRole('option').map((option) => option.textContent)).toEqual([
      'Classic',
      'Modern',
      'Compact',
      'Even',
      'Stack Overflow',
      'Kendall',
    ])
    await user.selectOptions(picker, 'Modern')
    // Picking alone asks nothing of the server: it waits for the button.
    expect(bodies).toEqual([])

    await user.click(screen.getByRole('button', { name: 'Tailor resume for Staff Engineer' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Tailoring your resume')
    expect(bodies).toEqual([{ template: 'MODERN' }])
  })

  it('offers the template the user last picked on a job not tailored yet', async () => {
    renderAction(3, 'COMPACT')

    expect(await screen.findByRole('combobox', { name: 'Resume template for Staff Engineer' })).toHaveValue('COMPACT')
  })

  it('lays a ready resume out in another template straight away', async () => {
    onServer = [tailoredResume({ status: 'READY', template: 'CLASSIC' })]
    const bodies: unknown[] = []
    server.use(
      http.post(`${API}/api/jobs/results/${JOB}/tailored-resume`, async ({ request }) => {
        bodies.push(await request.json())
        onServer = [tailoredResume({ status: 'READY', template: 'COMPACT' })]
        return HttpResponse.json(onServer[0])
      }),
    )
    const user = renderAction()

    const picker = await screen.findByRole('combobox', { name: 'Resume template for Staff Engineer' })
    expect(picker).toHaveValue('CLASSIC')
    await user.selectOptions(picker, 'Compact')

    expect(bodies).toEqual([{ template: 'COMPACT' }])
    await waitFor(() => expect(picker).toBeEnabled())
    expect(picker).toHaveValue('COMPACT')
    expect(screen.getByRole('button', { name: 'Download tailored resume for Staff Engineer' })).toBeEnabled()
    // Nothing to wait for: it never went back to tailoring.
    expect(screen.queryByText(/Tailoring your resume/)).not.toBeInTheDocument()
  })

  it('puts the template back and says why when it cannot be changed', async () => {
    onServer = [tailoredResume({ status: 'READY', template: 'CLASSIC' })]
    server.use(
      http.post(`${API}/api/jobs/results/${JOB}/tailored-resume`, () =>
        HttpResponse.json({ message: "we couldn't reach resume storage right now, please try again" }, { status: 503 }),
      ),
    )
    const user = renderAction()

    const picker = await screen.findByRole('combobox', { name: 'Resume template for Staff Engineer' })
    await user.selectOptions(picker, 'Modern')

    expect(await screen.findByRole('alert')).toHaveTextContent("We couldn't reach resume storage right now")
    expect(picker).toHaveValue('CLASSIC')
  })

  it('previews every template, then uses the one the user chooses', async () => {
    const user = renderAction()

    const previewButton = await screen.findByRole('button', { name: 'Preview templates for Staff Engineer' })
    await user.click(previewButton)

    const gallery = screen.getByRole('dialog', { name: 'Choose a template' })
    const choices = within(gallery).getAllByRole('button', { name: / template$/ })
    expect(choices.map((choice) => choice.getAttribute('aria-label'))).toEqual([
      'Classic template',
      'Modern template',
      'Compact template',
      'Even template',
      'Stack Overflow template',
      'Kendall template',
    ])
    const kendall = within(gallery).getByRole('button', { name: 'Kendall template' })
    expect(kendall.querySelector('img')).toHaveAttribute('src', `${API}/api/resume-templates/KENDALL/preview`)
    // The current choice is marked, and has focus to start from.
    const classic = within(gallery).getByRole('button', { name: 'Classic template' })
    expect(classic).toHaveAttribute('aria-pressed', 'true')
    expect(classic).toHaveFocus()

    await user.click(kendall)

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Resume template for Staff Engineer' })).toHaveValue('KENDALL')
    expect(previewButton).toHaveFocus()
  })

  it('lays a ready resume out in the template chosen from the preview', async () => {
    onServer = [tailoredResume({ status: 'READY', template: 'CLASSIC' })]
    const bodies: unknown[] = []
    server.use(
      http.post(`${API}/api/jobs/results/${JOB}/tailored-resume`, async ({ request }) => {
        bodies.push(await request.json())
        onServer = [tailoredResume({ status: 'READY', template: 'EVEN' })]
        return HttpResponse.json(onServer[0])
      }),
    )
    const user = renderAction()

    await user.click(await screen.findByRole('button', { name: 'Preview templates for Staff Engineer' }))
    // Choosing the one it already has asks nothing of the server.
    await user.click(screen.getByRole('button', { name: 'Classic template' }))
    expect(bodies).toEqual([])

    await user.click(screen.getByRole('button', { name: 'Preview templates for Staff Engineer' }))
    await user.click(screen.getByRole('button', { name: 'Even template' }))

    await waitFor(() => expect(bodies).toEqual([{ template: 'EVEN' }]))
    await waitFor(() =>
      expect(screen.getByRole('combobox', { name: 'Resume template for Staff Engineer' })).toHaveValue('EVEN'),
    )
  })

  it('closes the preview without changing anything, by Escape, the close button or clicking outside', async () => {
    const user = renderAction()
    const previewButton = await screen.findByRole('button', { name: 'Preview templates for Staff Engineer' })

    await user.click(previewButton)
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    await user.click(previewButton)
    await user.click(screen.getByRole('button', { name: 'Close' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    await user.click(previewButton)
    await user.click(screen.getByRole('dialog').parentElement!)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    expect(screen.getByRole('combobox', { name: 'Resume template for Staff Engineer' })).toHaveValue('CLASSIC')
  })

  it('downloads a tailored resume that is ready, through a fresh link', async () => {
    onServer = [tailoredResume({ status: 'READY' })]
    server.use(
      http.get(`${API}/api/jobs/results/${JOB}/tailored-resume/download`, () =>
        HttpResponse.json({ url: 'https://storage.test/tailored/r-1', expiresAt: '2026-10-13T09:05:00Z' }),
      ),
    )
    const user = renderAction()

    await user.click(await screen.findByRole('button', { name: 'Download tailored resume for Staff Engineer' }))

    await waitFor(() => expect(startDownload).toHaveBeenCalledWith('https://storage.test/tailored/r-1'))
    expect(screen.queryByRole('button', { name: /Tailor/ })).not.toBeInTheDocument()
  })

  it('sends a user with no resume to upload one, instead of offering to tailor', async () => {
    const user = renderAction(null)

    expect(await screen.findByText(/to tailor it to this job/)).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: 'Upload your resume' }))
    expect(await screen.findByText('Profile page')).toBeInTheDocument()
  })

  it('says why tailoring failed and lets the user try again', async () => {
    onServer = [tailoredResume({ status: 'FAILED', failureReason: "we couldn't find this job posting" })]
    const requests = vi.fn()
    server.use(
      http.post(`${API}/api/jobs/results/${JOB}/tailored-resume`, () => {
        requests()
        onServer = [tailoredResume({ status: 'PENDING', requestedAt: now(), completedAt: null })]
        return HttpResponse.json(onServer[0], { status: 202 })
      }),
    )
    const user = renderAction()

    expect(await screen.findByText("We couldn't find this job posting.")).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Try again for Staff Engineer' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Tailoring your resume')
    expect(requests).toHaveBeenCalledOnce()
    expect(screen.queryByText(/couldn't find this job posting/)).not.toBeInTheDocument()
  })

  it('offers to tailor again when the user has uploaded a newer resume, keeping the old download', async () => {
    onServer = [tailoredResume({ status: 'READY', resumeVersion: 2 })]
    renderAction(3)

    expect(await screen.findByText('Tailored from an earlier version of your resume.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Download tailored resume for Staff Engineer' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Tailor again for Staff Engineer' })).toBeEnabled()
  })

  it('lets the user start again when tailoring has been pending far too long', async () => {
    onServer = [tailoredResume({ status: 'PENDING', requestedAt: '2020-01-01T00:00:00Z', completedAt: null })]
    renderAction()

    expect(await screen.findByText('Tailoring is taking longer than it should.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Try again for Staff Engineer' })).toBeEnabled()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it("shows the server's reason when it refuses to start tailoring", async () => {
    server.use(
      http.post(`${API}/api/jobs/results/${JOB}/tailored-resume`, () =>
        HttpResponse.json({ message: 'upload your resume before tailoring it to a job' }, { status: 409 }),
      ),
    )
    const user = renderAction()

    await user.click(await screen.findByRole('button', { name: 'Tailor resume for Staff Engineer' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Upload your resume before tailoring it to a job')
    expect(screen.getByRole('button', { name: 'Tailor resume for Staff Engineer' })).toBeEnabled()
  })

  it('says so when a download link cannot be made', async () => {
    onServer = [tailoredResume({ status: 'READY' })]
    server.use(
      http.get(`${API}/api/jobs/results/${JOB}/tailored-resume/download`, () =>
        HttpResponse.json({ message: "we couldn't reach resume storage right now, please try again" }, { status: 503 }),
      ),
    )
    const user = renderAction()

    await user.click(await screen.findByRole('button', { name: 'Download tailored resume for Staff Engineer' }))

    expect(await screen.findByRole('alert')).toHaveTextContent("We couldn't reach resume storage right now")
    expect(startDownload).not.toHaveBeenCalled()
  })
})
