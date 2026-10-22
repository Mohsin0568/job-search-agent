import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { KnownCompany } from '../api/types'
import { renderRoutes } from '../test/render'
import { API, server } from '../test/server'
import { CompanyBrowser } from './CompanyBrowser'

const KNOWN: KnownCompany[] = [
  { name: 'Barclays', sector: 'Banking' },
  { name: 'Monzo', sector: 'Fintech' },
  { name: 'Wise', sector: 'Fintech' },
  { name: 'Lloyds Banking Group', sector: 'Banking' },
  { name: 'eBay', sector: 'Retail & E-commerce' },
]

const onDone = vi.fn()
const onCancel = vi.fn()

function renderBrowser(current: string[] = [], max = 10) {
  renderRoutes([
    { path: '/', element: <CompanyBrowser current={current} max={max} onDone={onDone} onCancel={onCancel} /> },
  ])
  return userEvent.setup()
}

const names = (list: HTMLElement) => within(list).getAllByRole('checkbox').map((box) => box.closest('label')?.textContent)

beforeEach(() => {
  onDone.mockReset()
  onCancel.mockReset()
  server.use(http.get(`${API}/api/companies`, () => HttpResponse.json(KNOWN)))
})

describe('CompanyBrowser', () => {
  it('lists every company A-Z with its sector, ticking the ones already chosen', async () => {
    renderBrowser(['monzo', 'Not Listed Ltd'])

    expect(screen.getByRole('dialog', { name: 'Browse companies' })).toBeInTheDocument()
    const list = await screen.findByRole('list', { name: 'Companies' })
    expect(names(list)).toEqual([
      'BarclaysBanking',
      'eBayRetail & E-commerce',
      'Lloyds Banking GroupBanking',
      'MonzoFintech',
      'WiseFintech',
    ])
    expect(screen.getByRole('checkbox', { name: /Monzo/ })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: /Wise/ })).not.toBeChecked()
    // The typed company that isn't listed still counts.
    expect(screen.getByText('2 of 10')).toBeInTheDocument()
  })

  it('lists the companies under their sectors when sorted by sector', async () => {
    const user = renderBrowser()
    await screen.findByRole('list', { name: 'Companies' })

    await user.click(screen.getByRole('radio', { name: 'By sector' }))

    expect(screen.getAllByRole('heading', { level: 3 }).map((heading) => heading.textContent)).toEqual([
      'Banking2',
      'Fintech2',
      'Retail & E-commerce1',
    ])
    expect(names(screen.getByRole('list', { name: 'Banking' }))).toEqual(['Barclays', 'Lloyds Banking Group'])
    expect(names(screen.getByRole('list', { name: 'Fintech' }))).toEqual(['Monzo', 'Wise'])
  })

  it('narrows the list to the companies matching the filter', async () => {
    const user = renderBrowser()
    await screen.findByRole('list', { name: 'Companies' })

    expect(screen.getByLabelText('Filter companies')).toHaveFocus()
    await user.type(screen.getByLabelText('Filter companies'), 'bank')

    expect(screen.getAllByRole('checkbox').map((box) => box.closest('label')?.textContent)).toEqual([
      'Lloyds Banking GroupBanking',
    ])

    await user.clear(screen.getByLabelText('Filter companies'))
    await user.type(screen.getByLabelText('Filter companies'), 'zzz')
    expect(screen.getByText(/No companies match “zzz”/)).toBeInTheDocument()
  })

  it('gives back the field with the unticked removed and the ticked added, keeping companies not listed', async () => {
    const user = renderBrowser(['monzo', 'Not Listed Ltd'])
    await screen.findByRole('list', { name: 'Companies' })

    await user.click(screen.getByRole('checkbox', { name: /Monzo/ }))
    await user.click(screen.getByRole('checkbox', { name: /Wise/ }))
    await user.click(screen.getByRole('checkbox', { name: /Barclays/ }))
    expect(screen.getByText('3 of 10')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Done' }))

    expect(onDone).toHaveBeenCalledWith(['Not Listed Ltd', 'Wise', 'Barclays'])
  })

  it('stops more being ticked at the limit', async () => {
    const user = renderBrowser(['Monzo'], 2)
    await screen.findByRole('list', { name: 'Companies' })

    await user.click(screen.getByRole('checkbox', { name: /Wise/ }))

    expect(screen.getByText(/Untick one to choose another/)).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: /Barclays/ })).toBeDisabled()
    expect(screen.getByRole('checkbox', { name: /Monzo/ })).toBeEnabled()

    await user.click(screen.getByRole('checkbox', { name: /Monzo/ }))
    expect(screen.getByRole('checkbox', { name: /Barclays/ })).toBeEnabled()
  })

  it('cancels without changing anything, from the button, the × or Escape', async () => {
    const user = renderBrowser(['Monzo'])
    await screen.findByRole('list', { name: 'Companies' })
    await user.click(screen.getByRole('checkbox', { name: /Wise/ }))

    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    await user.click(screen.getByRole('button', { name: 'Close' }))
    await user.keyboard('{Escape}')

    expect(onCancel).toHaveBeenCalledTimes(3)
    expect(onDone).not.toHaveBeenCalled()
  })

  it('says when the list can’t be loaded, and loads it on retry', async () => {
    let fail = true
    server.use(
      http.get(`${API}/api/companies`, () =>
        fail ? HttpResponse.json({ message: 'down' }, { status: 503 }) : HttpResponse.json(KNOWN),
      ),
    )
    const user = renderBrowser()

    expect(await screen.findByText('We couldn’t load the list of companies.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Done' })).toBeDisabled()

    fail = false
    await user.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('list', { name: 'Companies' })).toBeInTheDocument()
  })
})
