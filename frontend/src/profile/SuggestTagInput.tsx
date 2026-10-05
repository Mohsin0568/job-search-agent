import { useState } from 'react'
import { MIN_SUGGESTION_QUERY_LENGTH, useSuggestions } from '../api/hooks'
import type { SuggestionSource } from '../api/types'
import { TagInput, type TagInputProps } from '../components/form/TagInput'
import { useDebouncedValue } from '../hooks/useDebouncedValue'

const SUGGESTION_DEBOUNCE_MS = 200

type Props = Omit<TagInputProps, 'suggestions' | 'onDraftChange'> & {
  source: SuggestionSource
}

/** TagInput with autocomplete from a seeded list, so users pick a known spelling rather than typing it. */
export function SuggestTagInput({ source, ...props }: Props) {
  const [draft, setDraft] = useState('')
  const query = useDebouncedValue(draft.trim(), SUGGESTION_DEBOUNCE_MS)
  const { data } = useSuggestions(source, query)

  // Ignore results for an older query once the box is cleared or too short.
  const suggestions =
    draft.trim().length >= MIN_SUGGESTION_QUERY_LENGTH[source] ? (data ?? []).map((entry) => entry.name) : []

  return <TagInput {...props} suggestions={suggestions} onDraftChange={setDraft} />
}
