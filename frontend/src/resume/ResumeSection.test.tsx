import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MAX_RESUME_SIZE_BYTES } from '../api/types'
import { resumeVersion } from '../test/fixtures'
import { renderRoutes } from '../test/render'
import { API, server } from '../test/server'
import { startDownload } from './download'
import { ResumeSection } from './ResumeSection'

vi.mock('./download', () => ({ startDownload: vi.fn() }))

function renderSection() {
  renderRoutes([{ path: '/', element: <ResumeSection /> }])
  // applyAccept off, so a test can pick a file the input's accept attribute would hide in a real picker.
  return userEvent.setup({ applyAccept: false })
}

function pdf(name = 'jane-cv.pdf', size = 2048): File {
  return new File([new Uint8Array(size)], name, { type: 'application/pdf' })
}

function versionsAre(...versions: ReturnType<typeof resumeVersion>[]) {
  server.use(http.get(`${API}/api/profile/resume`, () => HttpResponse.json(versions)))
}

beforeEach(() => vi.mocked(startDownload).mockReset())

describe('ResumeSection', () => {
  it('invites a user with no resume to upload one', async () => {
    versionsAre()
    renderSection()

    expect(await screen.findByText('You haven’t uploaded a resume yet.')).toBeInTheDocument()
    expect(screen.getByLabelText('Upload your resume')).toBeEnabled()
    expect(screen.queryByText('Earlier versions')).not.toBeInTheDocument()
  })

  it('uploads the picked PDF and shows it as the current resume', async () => {
    versionsAre()
    const uploads = vi.fn()
    server.use(
      http.post(`${API}/api/profile/resume`, () => {
        uploads()
        return HttpResponse.json(resumeVersion({ version: 1, fileName: 'jane-cv.pdf' }), { status: 201 })
      }),
    )
    const user = renderSection()

    await user.upload(await screen.findByLabelText('Upload your resume'), pdf('jane-cv.pdf'))

    expect(await screen.findByRole('status')).toHaveTextContent('Your resume has been uploaded as version 1.')
    expect(uploads).toHaveBeenCalledOnce()
    expect(screen.getByText('jane-cv.pdf')).toBeInTheDocument()
    expect(screen.getByText(/^Version 1 · 2 KB · uploaded /)).toBeInTheDocument()
    expect(screen.getByLabelText('Upload a new version')).toBeEnabled()
  })

  it('shows the current resume, with earlier versions listed newest first', async () => {
    versionsAre(
      resumeVersion({ version: 3, latest: true, fileName: 'cv-2026.pdf' }),
      resumeVersion({ version: 2, latest: false, fileName: 'cv-2025.pdf' }),
      resumeVersion({ version: 1, latest: false, fileName: 'cv-2024.pdf' }),
    )
    renderSection()

    expect(await screen.findByText('cv-2026.pdf')).toBeInTheDocument()
    const earlier = within(screen.getByRole('list'))
    expect(earlier.getAllByRole('listitem').map((item) => item.textContent)).toEqual([
      expect.stringContaining('cv-2025.pdf'),
      expect.stringContaining('cv-2024.pdf'),
    ])
    expect(earlier.queryByText('cv-2026.pdf')).not.toBeInTheDocument()
  })

  it('moves the previous resume to earlier versions when a new one is uploaded', async () => {
    versionsAre(resumeVersion({ version: 1, latest: true, fileName: 'old-cv.pdf' }))
    server.use(
      http.post(`${API}/api/profile/resume`, () =>
        HttpResponse.json(resumeVersion({ version: 2, latest: true, fileName: 'new-cv.pdf' }), { status: 201 }),
      ),
    )
    const user = renderSection()

    await user.upload(await screen.findByLabelText('Upload a new version'), pdf('new-cv.pdf'))

    expect(await screen.findByText('new-cv.pdf')).toBeInTheDocument()
    expect(within(screen.getByRole('list')).getByText('old-cv.pdf')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Download version 1' })).toBeInTheDocument()
  })

  it.each([
    ['a file that is not a PDF', new File(['hello'], 'cv.docx', { type: 'application/msword' }), 'Your resume must be a PDF file.'],
    ['an empty file', pdf('empty.pdf', 0), 'That file is empty.'],
    ['a file over 5MB', pdf('huge.pdf', MAX_RESUME_SIZE_BYTES + 1), 'Your resume must be 5MB or smaller.'],
  ])('turns away %s without uploading it', async (_case, file, message) => {
    versionsAre()
    const uploads = vi.fn()
    server.use(
      http.post(`${API}/api/profile/resume`, () => {
        uploads()
        return HttpResponse.json(resumeVersion(), { status: 201 })
      }),
    )
    const user = renderSection()

    await user.upload(await screen.findByLabelText('Upload your resume'), file)

    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(uploads).not.toHaveBeenCalled()
  })

  it("shows the backend's reason when it rejects the upload, and lets the user try again", async () => {
    versionsAre()
    server.use(
      http.post(`${API}/api/profile/resume`, () =>
        HttpResponse.json({ message: "we couldn't read any text from this PDF" }, { status: 400 }),
      ),
    )
    const user = renderSection()

    await user.upload(await screen.findByLabelText('Upload your resume'), pdf())

    expect(await screen.findByRole('alert')).toHaveTextContent("we couldn't read any text from this PDF")
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Upload your resume')).toBeEnabled()
  })

  it('downloads the version asked for through a fresh link', async () => {
    versionsAre(resumeVersion({ version: 2, latest: true }), resumeVersion({ version: 1, latest: false }))
    server.use(
      http.get(`${API}/api/profile/resume/:version/download`, ({ params }) =>
        HttpResponse.json({ url: `https://storage.test/v${String(params.version)}`, expiresAt: '2026-10-12T09:05:00Z' }),
      ),
    )
    const user = renderSection()

    await user.click(await screen.findByRole('button', { name: 'Download version 1' }))
    await waitFor(() => expect(startDownload).toHaveBeenCalledWith('https://storage.test/v1'))

    await user.click(screen.getByRole('button', { name: 'Download your current resume' }))
    await waitFor(() => expect(startDownload).toHaveBeenLastCalledWith('https://storage.test/v2'))
  })

  it('says so when a download link cannot be made', async () => {
    versionsAre(resumeVersion({ version: 1, latest: true }))
    server.use(
      http.get(`${API}/api/profile/resume/1/download`, () =>
        HttpResponse.json({ message: "we couldn't reach resume storage right now, please try again" }, { status: 503 }),
      ),
    )
    const user = renderSection()

    await user.click(await screen.findByRole('button', { name: 'Download your current resume' }))

    expect(await screen.findByRole('alert')).toHaveTextContent("we couldn't reach resume storage right now")
    expect(startDownload).not.toHaveBeenCalled()
  })

  it('offers a retry when the resume cannot be loaded', async () => {
    server.use(
      http.get(`${API}/api/profile/resume`, () => HttpResponse.json({ message: 'Storage is down' }, { status: 503 })),
    )
    const user = renderSection()

    expect(await screen.findByRole('alert')).toHaveTextContent('We couldn’t load your resume. Storage is down')
    expect(screen.queryByLabelText('Upload your resume')).not.toBeInTheDocument()

    versionsAre(resumeVersion({ fileName: 'jane-cv.pdf' }))
    await user.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByText('jane-cv.pdf')).toBeInTheDocument()
  })
})
