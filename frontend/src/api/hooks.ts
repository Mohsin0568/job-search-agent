import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../auth/AuthContext'
import { getJobResults, getProfile, saveProfile, startJobSearch, suggestCompanies } from './endpoints'
import type { CandidateProfile } from './types'

// Query keys include the user id so one user's cached data is never shown to the next user on this browser.
const keys = {
  profile: (userId: string) => ['profile', userId] as const,
  jobResults: (userId: string, page: number, size: number) => ['jobResults', userId, page, size] as const,
  allJobResults: (userId: string) => ['jobResults', userId] as const,
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

export function useStartJobSearch() {
  const userId = useUserId()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: startJobSearch,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keys.allJobResults(userId) }),
  })
}

export const MIN_COMPANY_QUERY_LENGTH = 2

/** Company autocomplete. Not user-specific, so it's cached across users. */
export function useCompanySuggestions(query: string) {
  return useQuery({
    queryKey: ['companySuggestions', query],
    queryFn: ({ signal }) => suggestCompanies(query, signal),
    enabled: query.length >= MIN_COMPANY_QUERY_LENGTH,
    staleTime: 5 * 60 * 1000,
    // Keep showing the last list while the next one loads, so the dropdown doesn't flicker.
    placeholderData: keepPreviousData,
  })
}
