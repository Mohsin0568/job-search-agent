import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../auth/AuthContext'
import { getJobResults, getProfile, saveProfile, startJobSearch } from './endpoints'
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
