import { useState } from 'react'
import { MIN_COMPANY_QUERY_LENGTH, useCompanySuggestions } from '../api/hooks'
import { TagInput, type TagInputProps } from '../components/form/TagInput'
import { useDebouncedValue } from '../hooks/useDebouncedValue'

const SUGGESTION_DEBOUNCE_MS = 200

/** TagInput with company-name autocomplete, so users pick the spelling job boards use rather than typing it. */
export function CompanyTagInput(props: Omit<TagInputProps, 'suggestions' | 'onDraftChange'>) {
  const [draft, setDraft] = useState('')
  const query = useDebouncedValue(draft.trim(), SUGGESTION_DEBOUNCE_MS)
  const { data } = useCompanySuggestions(query)

  // Ignore results for an older query once the box is cleared or too short.
  const suggestions = draft.trim().length >= MIN_COMPANY_QUERY_LENGTH ? (data ?? []).map((company) => company.name) : []

  return <TagInput {...props} suggestions={suggestions} onDraftChange={setDraft} />
}
