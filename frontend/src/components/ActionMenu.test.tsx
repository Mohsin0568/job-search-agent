import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ActionMenu, type MenuAction } from './ActionMenu'

function renderMenu(actions: MenuAction[]) {
  render(
    <div>
      <ActionMenu label="More actions for Staff Engineer" actions={actions} />
      <button type="button">Elsewhere</button>
    </div>,
  )
  return screen.getByRole('button', { name: 'More actions for Staff Engineer' })
}

const actions = (onSelect = vi.fn()): MenuAction[] => [
  { label: 'Applied', accessibleLabel: 'Mark Staff Engineer as applied', onSelect },
  { label: 'Not relevant', onSelect },
  { label: 'Tailor as is', onSelect },
]

describe('ActionMenu', () => {
  it('opens on click, on its first action, and says it is open', async () => {
    const button = renderMenu(actions())
    expect(button).toHaveAttribute('aria-haspopup', 'menu')
    expect(button).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()

    await userEvent.click(button)

    expect(button).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByRole('menu', { name: 'More actions for Staff Engineer' })).toBeInTheDocument()
    expect(screen.getByRole('menuitem', { name: 'Mark Staff Engineer as applied' })).toHaveFocus()
  })

  it('runs the chosen action and closes, back on the menu button', async () => {
    const onSelect = vi.fn()
    const button = renderMenu(actions(onSelect))

    await userEvent.click(button)
    await userEvent.click(screen.getByRole('menuitem', { name: 'Not relevant' }))

    expect(onSelect).toHaveBeenCalledOnce()
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
    expect(button).toHaveFocus()
  })

  it('moves between actions with the arrow keys, wrapping round, and runs one with Enter', async () => {
    const onSelect = vi.fn()
    const button = renderMenu(actions(onSelect))
    button.focus()

    await userEvent.keyboard('{ArrowDown}')
    expect(screen.getByRole('menuitem', { name: 'Mark Staff Engineer as applied' })).toHaveFocus()
    await userEvent.keyboard('{ArrowDown}{ArrowDown}')
    expect(screen.getByRole('menuitem', { name: 'Tailor as is' })).toHaveFocus()
    await userEvent.keyboard('{ArrowDown}')
    expect(screen.getByRole('menuitem', { name: 'Mark Staff Engineer as applied' })).toHaveFocus()
    await userEvent.keyboard('{ArrowUp}')
    expect(screen.getByRole('menuitem', { name: 'Tailor as is' })).toHaveFocus()
    await userEvent.keyboard('{Home}')
    expect(screen.getByRole('menuitem', { name: 'Mark Staff Engineer as applied' })).toHaveFocus()

    await userEvent.keyboard('{Enter}')
    expect(onSelect).toHaveBeenCalledOnce()
  })

  it('closes on Escape without running anything', async () => {
    const onSelect = vi.fn()
    const button = renderMenu(actions(onSelect))

    await userEvent.click(button)
    await userEvent.keyboard('{Escape}')

    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
    expect(button).toHaveFocus()
    expect(onSelect).not.toHaveBeenCalled()
  })

  it('closes when the user clicks elsewhere', async () => {
    const button = renderMenu(actions())

    await userEvent.click(button)
    await userEvent.click(screen.getByRole('button', { name: 'Elsewhere' }))

    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })

  it('skips an action that is unavailable', async () => {
    const onSelect = vi.fn()
    const button = renderMenu([
      { label: 'Applied', onSelect, disabled: true },
      { label: 'Not relevant', onSelect },
    ])

    await userEvent.click(button)

    expect(screen.getByRole('menuitem', { name: 'Applied' })).toBeDisabled()
    expect(screen.getByRole('menuitem', { name: 'Not relevant' })).toHaveFocus()
  })

  it('shows nothing when there are no actions', () => {
    render(<ActionMenu label="More actions" actions={[]} />)

    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })
})
