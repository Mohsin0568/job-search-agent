import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import type { JobSearchResult, JobStatus } from '../api/types'
import { result, resumeReview } from '../test/fixtures'
import { renderRoutes } from '../test/render'
import { API, server } from '../test/server'
import { MarkedJobCard } from './MarkedJobCard'

type Sent = { status: JobStatus; reason: string | null }

// Answers every status change the way the backend does, and records what was sent.
function acceptStatusChanges(sent: Sent[]) {
  server.use(
    http.put(`${API}/api/jobs/results/:id/status`, async ({ request, params }) => {
      const body = (await request.json()) as Sent
      sent.push(body)
      return HttpResponse.json(
        result(String(params.id), 'Acme Corp', {}, {
          status: body.status,
          statusChangedAt: '2026-10-20T09:00:00Z',
          notRelevantReason: body.status === 'NOT_RELEVANT' ? (body.reason?.trim() || null) : null,
        }),
      )
    }),
  )
}

const applied = () => result('r-1', 'Acme Corp', {}, { status: 'APPLIED', statusChangedAt: '2026-10-19T09:00:00Z' })
const notRelevant = (notRelevantReason: string | null = 'Too senior') =>
  result('r-1', 'Acme Corp', {}, { status: 'NOT_RELEVANT', statusChangedAt: '2026-10-19T09:00:00Z', notRelevantReason })

function renderCard(job: JobSearchResult, onMoved = vi.fn(), review = resumeReview()) {
  renderRoutes([{ path: '/', element: <MarkedJobCard result={job} review={review} onMoved={onMoved} /> }])
  return onMoved
}

/** Opens the card's ⋮ menu and picks an action from it. */
async function choose(action: string) {
  await userEvent.click(screen.getByRole('button', { name: 'More actions for Backend Engineer' }))
  await userEvent.click(screen.getByRole('menuitem', { name: action }))
}

const menuItems = async () => {
  await userEvent.click(screen.getByRole('button', { name: 'More actions for Backend Engineer' }))
  const items = screen.getAllByRole('menuitem').map((item) => item.textContent)
  await userEvent.keyboard('{Escape}')
  return items
}

describe('MarkedJobCard', () => {
  it('shows when the job was marked and why, with the status changes in its menu rather than on the card', async () => {
    renderCard(notRelevant('Needs relocation'))

    expect(screen.getByText(/^Marked not relevant on /)).toBeInTheDocument()
    expect(screen.getByText('Needs relocation')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /back to your dashboard/ })).not.toBeInTheDocument()
    expect(await menuItems()).toEqual(['Applied', 'Edit reason', 'Back to dashboard'])
  })

  it('offers an applied job the other mark and a way back to the dashboard', async () => {
    renderCard(applied())

    expect(await menuItems()).toEqual(['Not relevant', 'Back to dashboard'])
  })

  it('moves a job back to the dashboard', async () => {
    const sent: Sent[] = []
    acceptStatusChanges(sent)
    const job = applied()
    const onMoved = renderCard(job)

    await choose('Move Backend Engineer back to your dashboard')

    await vi.waitFor(() => expect(onMoved).toHaveBeenCalledOnce())
    expect(sent).toEqual([{ status: 'ACTIVE', reason: null }])
    expect(onMoved.mock.calls[0][0]).toBe(job)
    expect(onMoved.mock.calls[0][1]).toMatchObject({ id: 'r-1', status: 'ACTIVE' })
  })

  it('marks a not-relevant job as applied', async () => {
    const sent: Sent[] = []
    acceptStatusChanges(sent)
    const onMoved = renderCard(notRelevant())

    await choose('Mark Backend Engineer as applied')

    await vi.waitFor(() => expect(onMoved).toHaveBeenCalledOnce())
    expect(sent).toEqual([{ status: 'APPLIED', reason: null }])
  })

  it('marks an applied job not relevant with the user’s reason, built partly from the review', async () => {
    const sent: Sent[] = []
    acceptStatusChanges(sent)
    const onMoved = renderCard(applied())

    await choose('Mark Backend Engineer as not relevant')
    const reason = screen.getByLabelText(/Why isn’t it relevant/)
    expect(reason).toHaveFocus()
    await userEvent.type(reason, 'Too senior')
    await userEvent.click(screen.getByRole('button', { name: 'Add “Missing Kubernetes” to the reason' }))
    // Once added, a phrase isn't offered again.
    expect(screen.queryByRole('button', { name: 'Add “Missing Kubernetes” to the reason' })).not.toBeInTheDocument()
    expect(reason).toHaveValue('Too senior; Missing Kubernetes')
    await userEvent.click(screen.getByRole('button', { name: 'Mark not relevant' }))

    await vi.waitFor(() => expect(onMoved).toHaveBeenCalledOnce())
    expect(sent).toEqual([{ status: 'NOT_RELEVANT', reason: 'Too senior; Missing Kubernetes' }])
  })

  it('lets the reason be left out', async () => {
    const sent: Sent[] = []
    acceptStatusChanges(sent)
    const onMoved = renderCard(applied(), vi.fn(), resumeReview({ status: 'PENDING' }))

    await choose('Mark Backend Engineer as not relevant')
    expect(screen.queryByText('Add from the resume review:')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Mark not relevant' }))

    await vi.waitFor(() => expect(onMoved).toHaveBeenCalledOnce())
    expect(sent).toEqual([{ status: 'NOT_RELEVANT', reason: null }])
  })

  it('cancels without changing anything', async () => {
    const sent: Sent[] = []
    acceptStatusChanges(sent)
    renderCard(applied())

    await choose('Mark Backend Engineer as not relevant')
    await userEvent.type(screen.getByLabelText(/Why isn’t it relevant/), 'Too senior')
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(screen.queryByLabelText(/Why isn’t it relevant/)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'More actions for Backend Engineer' })).toBeInTheDocument()
    expect(sent).toEqual([])
  })

  it('says how much room is left once the reason nears the limit', async () => {
    renderCard(applied())

    await choose('Mark Backend Engineer as not relevant')
    const reason = screen.getByLabelText(/Why isn’t it relevant/)
    expect(reason).toHaveAttribute('maxLength', '500')
    await userEvent.click(reason)
    await userEvent.paste('x'.repeat(450))

    expect(screen.getByText('50 characters left')).toBeInTheDocument()
  })

  it('edits the reason of a not-relevant job in place, without moving it', async () => {
    const sent: Sent[] = []
    acceptStatusChanges(sent)
    const onMoved = renderCard(notRelevant())

    await choose('Edit why Backend Engineer isn’t relevant')
    const reason = screen.getByLabelText(/Why isn’t it relevant/)
    expect(reason).toHaveValue('Too senior')
    // The reason is in the form now, not shown twice.
    expect(screen.queryByText('Too senior', { selector: 'blockquote' })).not.toBeInTheDocument()
    await userEvent.clear(reason)
    await userEvent.type(reason, 'Needs relocation')
    await userEvent.click(screen.getByRole('button', { name: 'Save reason' }))

    await vi.waitFor(() => expect(screen.queryByLabelText(/Why isn’t it relevant/)).not.toBeInTheDocument())
    expect(sent).toEqual([{ status: 'NOT_RELEVANT', reason: 'Needs relocation' }])
    expect(onMoved).not.toHaveBeenCalled()
  })

  it('says so when the job can’t be updated', async () => {
    server.use(http.put(`${API}/api/jobs/results/:id/status`, () => HttpResponse.json({ message: 'boom' }, { status: 500 })))
    const onMoved = renderCard(applied())

    await choose('Move Backend Engineer back to your dashboard')

    expect(await screen.findByRole('alert')).toHaveTextContent('Couldn’t update this job. Please try again.')
    expect(onMoved).not.toHaveBeenCalled()
  })
})
