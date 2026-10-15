import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../auth/AuthContext'
import {
  getJobResults,
  getProfile,
  getResumeDownload,
  getResumeReviews,
  getResumeVersions,
  getTailoredResumeDownload,
  getTailoredResumes,
  requestResumeReview,
  requestTailoredResume,
  saveProfile,
  suggestNames,
  uploadResume,
} from './endpoints'
import { isReviewing, REVIEW_POLL_INTERVAL_MS } from '../resume/review'
import { isTailoring, TAILORING_POLL_INTERVAL_MS } from '../resume/tailoring'
import type {
  CandidateProfile,
  ResumeReview,
  ResumeTemplate,
  ResumeVersion,
  SuggestionSource,
  TailoredResume,
  TailoringFix,
} from './types'

// Query keys include the user id so one user's cached data is never shown to the next user on this browser.
const keys = {
  profile: (userId: string) => ['profile', userId] as const,
  jobResults: (userId: string, page: number, size: number) => ['jobResults', userId, page, size] as const,
  resumeVersions: (userId: string) => ['resumeVersions', userId] as const,
  tailoredResumes: (userId: string) => ['tailoredResumes', userId] as const,
  resumeReviews: (userId: string) => ['resumeReviews', userId] as const,
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

export function useResumeVersions() {
  const userId = useUserId()
  return useQuery({ queryKey: keys.resumeVersions(userId), queryFn: getResumeVersions })
}

export function useUploadResume() {
  const userId = useUserId()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (file: File) => uploadResume(file),
    // The new version is the latest now; the ones already listed no longer are.
    onSuccess: (uploaded) =>
      queryClient.setQueryData<ResumeVersion[]>(keys.resumeVersions(userId), (versions = []) => [
        uploaded,
        ...versions.map((version) => ({ ...version, latest: false })),
      ]),
  })
}

/** Asks for a fresh download link each time: they expire after a few minutes. */
export function useResumeDownload() {
  return useMutation({ mutationFn: (version: number) => getResumeDownload(version) })
}

/** The user's tailored resumes. Asks again every few seconds for as long as one is being tailored. */
export function useTailoredResumes() {
  const userId = useUserId()
  return useQuery({
    queryKey: keys.tailoredResumes(userId),
    queryFn: getTailoredResumes,
    refetchInterval: (query) =>
      query.state.data?.some((tailored) => isTailoring(tailored)) ? TAILORING_POLL_INTERVAL_MS : false,
  })
}

export function useRequestTailoredResume() {
  const userId = useUserId()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ jobResultId, template, fix }: { jobResultId: string; template?: ResumeTemplate; fix?: TailoringFix }) =>
      requestTailoredResume(jobResultId, { template, fix }),
    // Putting the PENDING record in the list is what starts useTailoredResumes polling for it.
    onSuccess: (requested) =>
      queryClient.setQueryData<TailoredResume[]>(keys.tailoredResumes(userId), (tailoredResumes = []) => [
        ...tailoredResumes.filter((tailored) => tailored.jobResultId !== requested.jobResultId),
        requested,
      ]),
  })
}

/** Asks for a fresh download link each time: they expire after a few minutes. */
export function useTailoredResumeDownload() {
  return useMutation({ mutationFn: (jobResultId: string) => getTailoredResumeDownload(jobResultId) })
}

/** The user's resume reviews. Asks again every few seconds for as long as one is being reviewed. */
export function useResumeReviews() {
  const userId = useUserId()
  return useQuery({
    queryKey: keys.resumeReviews(userId),
    queryFn: getResumeReviews,
    refetchInterval: (query) =>
      query.state.data?.some((review) => isReviewing(review)) ? REVIEW_POLL_INTERVAL_MS : false,
  })
}

export function useRequestResumeReview() {
  const userId = useUserId()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (jobResultId: string) => requestResumeReview(jobResultId),
    // Putting the PENDING record in the list is what starts useResumeReviews polling for it.
    onSuccess: (requested) =>
      queryClient.setQueryData<ResumeReview[]>(keys.resumeReviews(userId), (reviews = []) => [
        ...reviews.filter((review) => review.jobResultId !== requested.jobResultId),
        requested,
      ]),
  })
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
