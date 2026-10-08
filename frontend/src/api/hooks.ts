import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../auth/AuthContext'
import { getJobResults, getProfile, saveProfile, suggestNames } from './endpoints'
import type { CandidateProfile, SuggestionSource } from './types'

// Query keys include the user id so one user's cached data is never shown to the next user on this browser.
const keys = {
  profile: (userId: string) => ['profile', userId] as const,
  jobResults: (userId: string, page: number, size: number) => ['jobResults', userId, page, size] as const,
}

function useUserId(): string {
  const { user } = useAuth()
  if (!user) {
    throw new Error('API hooks must be used inside a ProtectedRoute')
  }
  return user.userId
}

export function useProfile() {
  const userId = useUserId()
  return useQuery({ queryKey: keys.profile(userId), queryFn: getProfile })
}

export function useSaveProfile() {
  const userId = useUserId()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (profile: CandidateProfile) => saveProfile(profile),
    onSuccess: (saved) => queryClient.setQueryData(keys.profile(userId), saved),
  })
}

export function useJobResults(page = 0, size = 50) {
  const userId = useUserId()
  return useQuery({ queryKey: keys.jobResults(userId, page, size), queryFn: () => getJobResults(page, size) })
}

// Skills can be a single letter ("C", "R"), so they suggest from the first keystroke.
export const MIN_SUGGESTION_QUERY_LENGTH: Record<SuggestionSource, number> = {
  companies: 2,
  skills: 1,
}

/** Company or skill autocomplete. Not user-specific, so it's cached across users. */
export function useSuggestions(source: SuggestionSource, query: string) {
  return useQuery({
    queryKey: ['suggestions', source, query],
    queryFn: ({ signal }) => suggestNames(source, query, signal),
    enabled: query.length >= MIN_SUGGESTION_QUERY_LENGTH[source],
    staleTime: 5 * 60 * 1000,
    // Keep showing the last list while the next one loads, so the dropdown doesn't flicker.
    placeholderData: keepPreviousData,
  })
}
