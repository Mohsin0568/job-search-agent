import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { CandidateProfile } from '../api/types'
import { profile } from '../test/fixtures'
import { renderRoutes } from '../test/render'
import { API, server } from '../test/server'
import { ProfileForm } from './ProfileForm'

const onSaved = vi.fn()
let savedBodies: unknown[]

function renderForm(initialProfile?: CandidateProfile) {
  renderRoutes([{ path: '/', element: <ProfileForm initialProfile={initialProfile} submitLabel="Save" onSaved={onSaved} /> }])
  return userEvent.setup()
}

const role = () => screen.getByLabelText(/Role you/)
const companies = () => screen.getByLabelText(/Companies to search/)
const skills = () => screen.getByLabelText(/Key skills/)
const save = () => screen.getByRole('button', { name: 'Save' })

beforeEach(() => {
  onSaved.mockReset()
  savedBodies = []
  server.use(
    http.get(`${API}/api/:source/suggest`, () => HttpResponse.json([])),
    http.put(`${API}/api/profile`, async ({ request }) => {
      const body = await request.json()
      savedBodies.push(body)
      return HttpResponse.json(body)
    }),
  )
})

describe('ProfileForm', () => {
  it('saves what the user entered', async () => {
    const user = renderForm()

    await user.type(role(), 'Senior Java Developer')
    await user.type(companies(), 'Acme Corp{Enter}Globex{Enter}')
    await user.type(skills(), 'Java{Enter}')
    await user.selectOptions(screen.getByLabelText('Only show jobs posted in the last'), '30 days')
    await user.click(save())

    const expected = {
      desiredRole: 'Senior Java Developer',
      skills: ['Java'],
      currentJobDescription: null,
      companyPreferences: ['Acme Corp', 'Globex'],
      recencyWindowDays: 30,
    }
    await waitFor(() => expect(onSaved).toHaveBeenCalledWith(expected))
    expect(savedBodies).toEqual([expected])
  })

  it('starts from the existing profile and saves only what changed', async () => {
    const user = renderForm(profile())

    expect(role()).toHaveValue('Senior Java Developer')
    expect(screen.getByLabelText('Current role (optional)')).toHaveValue('Builds APIs')
    expect(screen.getByLabelText('Only show jobs posted in the last')).toHaveValue('14')
    expect(screen.getByText('(2/10)')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Remove Globex' }))
    await user.click(save())

    await waitFor(() => expect(savedBodies).toEqual([profile({ companyPreferences: ['Acme Corp'] })]))
  })

  it('defaults to jobs from the last 7 days', () => {
    renderForm()

    expect(screen.getByLabelText('Only show jobs posted in the last')).toHaveValue('7')
  })

  it('needs a role and at least one company before saving', async () => {
    const user = renderForm()

    await user.click(save())

    expect(await screen.findByText('Enter the role you’re looking for')).toBeInTheDocument()
    expect(screen.getByText('Add at least one company to search')).toBeInTheDocument()
    expect(role()).toHaveAttribute('aria-invalid', 'true')
    expect(savedBodies).toEqual([])
    expect(onSaved).not.toHaveBeenCalled()
  })

  it('stops taking companies at the limit of 10', async () => {
    const user = renderForm()

    for (let i = 1; i <= 10; i++) {
      await user.type(companies(), `Company ${i}{Enter}`)
    }

    expect(screen.getByText('(10/10)')).toBeInTheDocument()
    expect(companies()).toBeDisabled()
  })

  it('offers known companies as the user types and adds the one picked', async () => {
    server.use(
      http.get(`${API}/api/companies/suggest`, ({ request }) =>
        HttpResponse.json(new URL(request.url).searchParams.get('q') === 'del' ? [{ name: 'Deliveroo' }, { name: 'Dell' }] : []),
      ),
    )
    const user = renderForm()

    await user.type(companies(), 'del')
    await user.click(await screen.findByRole('option', { name: 'Dell' }))

    expect(screen.getByRole('button', { name: 'Remove Dell' })).toBeInTheDocument()
    expect(companies()).toHaveValue('')
  })

  it('suggests skills from the first letter, but companies only from the second', async () => {
    const queries: string[] = []
    server.use(
      http.get(`${API}/api/:source/suggest`, ({ request, params }) => {
        queries.push(`${String(params.source)}: ${new URL(request.url).searchParams.get('q')}`)
        return HttpResponse.json([{ name: 'C#' }])
      }),
    )
    const user = renderForm()

    await user.type(companies(), 'c')
    await user.type(skills(), 'c')

    expect(await screen.findByRole('option', { name: 'C#' })).toBeInTheDocument()
    expect(queries).toEqual(['skills: c'])
  })

  it("shows the backend's message when the save is rejected", async () => {
    server.use(
      http.put(`${API}/api/profile`, () => HttpResponse.json({ message: 'desiredRole must not be blank' }, { status: 400 })),
    )
    const user = renderForm(profile())

    await user.click(save())

    expect(await screen.findByRole('alert')).toHaveTextContent('desiredRole must not be blank')
    expect(onSaved).not.toHaveBeenCalled()
    expect(save()).toBeEnabled()
  })
})
