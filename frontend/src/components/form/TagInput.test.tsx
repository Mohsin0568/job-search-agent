import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { TagInput, type TagInputProps } from './TagInput'

type Options = Partial<Pick<TagInputProps, 'maxItems' | 'suggestions' | 'onDraftChange'>> & { initial?: string[] }

/** Holds the value like a form would, and shows it so tests can read the list back. */
function Harness({ initial = [], maxItems = 5, ...props }: Options) {
  const [value, setValue] = useState(initial)
  return (
    <>
      <TagInput label="Companies" value={value} onChange={setValue} maxItems={maxItems} maxItemLength={50} {...props} />
      <output>{value.join(' | ')}</output>
    </>
  )
}

function setup(options: Options = {}) {
  render(<Harness {...options} />)
  return {
    user: userEvent.setup(),
    input: screen.getByLabelText(/Companies/),
    chosen: () => screen.getByRole('status').textContent,
  }
}

// What SuggestTagInput passes once the backend has answered for the typed text.
const withSuggestions = { suggestions: ['Deliveroo', 'Dell', 'Deloitte'], onDraftChange: vi.fn() }

describe('TagInput', () => {
  it('adds what was typed on Enter, comma, or leaving the field', async () => {
    const { user, input, chosen } = setup()

    await user.type(input, 'Acme Corp{Enter}')
    await user.type(input, '  Globex ,')
    await user.type(input, 'Initech')
    await user.tab()

    expect(chosen()).toBe('Acme Corp | Globex | Initech')
    expect(input).toHaveValue('')
  })

  it('ignores an empty entry', async () => {
    const { user, input, chosen } = setup()

    await user.type(input, '   {Enter}')

    expect(chosen()).toBe('')
  })

  it('does not add the same name twice, ignoring case, accents and spacing', async () => {
    const { user, input, chosen } = setup({ initial: ['Nestlé'] })

    await user.type(input, 'nestle{Enter}')
    await user.type(input, ' NESTLÉ {Enter}')

    expect(chosen()).toBe('Nestlé')
  })

  it('keeps names that differ only by symbols apart', async () => {
    const { user, input, chosen } = setup()

    await user.type(input, 'C{Enter}C++{Enter}C#{Enter}.NET{Enter}')

    expect(chosen()).toBe('C | C++ | C# | .NET')
  })

  it('removes an entry with its button, or the last one with Backspace in the empty box', async () => {
    const { user, input, chosen } = setup({ initial: ['Acme Corp', 'Globex', 'Initech'] })

    await user.click(screen.getByRole('button', { name: 'Remove Acme Corp' }))
    expect(chosen()).toBe('Globex | Initech')

    await user.type(input, '{Backspace}')
    expect(chosen()).toBe('Globex')
  })

  it('only deletes text, not an entry, when Backspace is pressed with text in the box', async () => {
    const { user, input, chosen } = setup({ initial: ['Acme Corp'] })

    await user.type(input, 'ab{Backspace}')

    expect(input).toHaveValue('a')
    expect(chosen()).toBe('Acme Corp')
  })

  it('shows how many entries are used and stops accepting more at the limit', async () => {
    const { user, input, chosen } = setup({ maxItems: 2, initial: ['Acme Corp'] })
    expect(screen.getByText('(1/2)')).toBeInTheDocument()

    await user.type(input, 'Globex{Enter}')

    expect(screen.getByText('(2/2)')).toBeInTheDocument()
    expect(input).toBeDisabled()
    expect(input).toHaveAttribute('placeholder', 'Limit reached')
    expect(chosen()).toBe('Acme Corp | Globex')
  })

  describe('with suggestions', () => {
    it('reports what is typed so suggestions can be fetched', async () => {
      const onDraftChange = vi.fn()
      const { user, input } = setup({ onDraftChange })

      await user.type(input, 'de')

      expect(onDraftChange.mock.calls).toEqual([['d'], ['de']])
    })

    it('lists them once the user types, leaving out names already chosen', async () => {
      const { user, input } = setup({ ...withSuggestions, initial: ['dell'] })
      expect(screen.queryByRole('listbox')).not.toBeInTheDocument()

      await user.type(input, 'de')

      expect(screen.getAllByRole('option').map((option) => option.textContent)).toEqual(['Deliveroo', 'Deloitte'])
      expect(input).toHaveAttribute('aria-expanded', 'true')
    })

    it('completes a half-typed name with the first suggestion on Enter', async () => {
      const { user, input, chosen } = setup(withSuggestions)

      await user.type(input, 'delivero{Enter}')

      expect(chosen()).toBe('Deliveroo')
      expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
    })

    it('adds the text as typed when no suggestion completes it', async () => {
      const { user, input, chosen } = setup(withSuggestions)

      await user.type(input, 'Acme Corp{Enter}')

      expect(chosen()).toBe('Acme Corp')
    })

    it('uses the suggested spelling when the typed text matches a suggestion', async () => {
      // "Dell" is not the first suggestion, so this is the exact-match rule, not the Enter default.
      const { user, input, chosen } = setup({ ...withSuggestions, suggestions: ['Deliveroo', 'Dell'] })

      await user.type(input, 'dell')
      await user.tab()

      expect(chosen()).toBe('Dell')
    })

    it('picks a suggestion with the arrow keys', async () => {
      const { user, input, chosen } = setup(withSuggestions)

      await user.type(input, 'de{ArrowDown}{ArrowDown}')
      expect(screen.getByRole('option', { name: 'Deloitte' })).toHaveAttribute('aria-selected', 'true')

      await user.keyboard('{ArrowUp}{Enter}')

      expect(chosen()).toBe('Dell')
    })

    it('picks a suggestion with the mouse', async () => {
      const { user, input, chosen } = setup(withSuggestions)

      await user.type(input, 'de')
      await user.click(screen.getByRole('option', { name: 'Deloitte' }))

      expect(chosen()).toBe('Deloitte')
    })

    it('closes the list on Escape, after which Enter adds the text as typed', async () => {
      const { user, input, chosen } = setup(withSuggestions)

      await user.type(input, 'de{Escape}')
      expect(screen.queryByRole('listbox')).not.toBeInTheDocument()

      await user.keyboard('{Enter}')

      expect(chosen()).toBe('de')
    })
  })
})
