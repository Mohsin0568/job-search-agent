import { keepPreviousData, useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../auth/AuthContext'
import {
  getJobResults,
  getJobSearchRun,
  getJobSearchRuns,
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
  updateJobStatus,
  uploadResume,
} from './endpoints'
import { isReviewing, REVIEW_POLL_INTERVAL_MS } from '../resume/review'
import { isTailoring, TAILORING_POLL_INTERVAL_MS } from '../resume/tailoring'
import type {
  CandidateProfile,
  JobSearchResult,
  JobStatus,
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
  // Every list of the user's jobs starts ['jobResults', userId, status], so marking a job can find them all.
  jobResults: (userId: string, status: JobStatus, page: number, size: number) =>
    ['jobResults', userId, status, page, size] as const,
  markedJobs: (userId: string, status: JobStatus) => ['jobResults', userId, status, 'pages'] as const,
  resumeVersions: (userId: string) => ['resumeVersions', userId] as const,
  tailoredResumes: (userId: string) => ['tailoredResumes', userId] as const,
  resumeReviews: (userId: string) => ['resumeReviews', userId] as const,
  jobSearchRuns: (userId: string) => ['jobSearchRuns', userId] as const,
  jobSearchRun: (userId: string, runId: string) => ['jobSearchRun', userId, runId] as const,
}

/** Runs per page of the search history. */
export const SEARCH_RUNS_PAGE_SIZE = 20

/** Jobs per page of the applied and not-relevant lists. */
export const MARKED_JOBS_PAGE_SIZE = 20

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

/** A page of the user's jobs with that status - by default the ones on their dashboard. */
export function useJobResults(page = 0, size = 50, status: JobStatus = 'ACTIVE') {
  const userId = useUserId()
  return useQuery({
    queryKey: keys.jobResults(userId, status, page, size),
    queryFn: () => getJobResults(page, size, status),
  })
}

/** The jobs the user has marked with that status, last marked first, a page at a time. */
export function useMarkedJobs(status: Exclude<JobStatus, 'ACTIVE'>) {
  const userId = useUserId()
  return useInfiniteQuery({
    queryKey: keys.markedJobs(userId, status),
    queryFn: ({ pageParam }) => getJobResults(pageParam, MARKED_JOBS_PAGE_SIZE, status),
    initialPageParam: 0,
    // A full page may have more after it; a short one is the last.
    getNextPageParam: (lastPage, _pages, lastPageParam) =>
      lastPage.length === MARKED_JOBS_PAGE_SIZE ? lastPageParam + 1 : undefined,
  })
}

type JobPages = { pages: JobSearchResult[][]; pageParams: unknown[] }

// The job as it now is, in a list of jobs with that status: kept up to date if it belongs there, or dropped.
function placeIn(jobs: JobSearchResult[], listStatus: unknown, updated: JobSearchResult): JobSearchResult[] {
  return updated.status === listStatus
    ? jobs.map((job) => (job.id === updated.id ? updated : job))
    : jobs.filter((job) => job.id !== updated.id)
}

/**
 * Marks one of the user's jobs. It leaves the lists it no longer belongs in straight away; the lists are
 * then fetched again, so it turns up in the right place in the one it has moved to.
 */
export function useUpdateJobStatus() {
  const userId = useUserId()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ jobResultId, status, reason }: { jobResultId: string; status: JobStatus; reason?: string }) =>
      updateJobStatus(jobResultId, status, reason),
    onSuccess: async (updated) => {
      for (const [key, data] of queryClient.getQueriesData<JobSearchResult[] | JobPages>({
        queryKey: ['jobResults', userId],
      })) {
        if (!data) continue
        const listStatus = key[2]
        queryClient.setQueryData(
          key,
          Array.isArray(data)
            ? placeIn(data, listStatus, updated)
            : { ...data, pages: data.pages.map((page) => placeIn(page, listStatus, updated)) },
        )
      }
      await queryClient.invalidateQueries({ queryKey: ['jobResults', userId] })
    },
  })
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

/** The user's search history, newest run first, a page at a time: fetchNextPage loads older runs. */
export function useJobSearchRuns() {
  const userId = useUserId()
  return useInfiniteQuery({
    queryKey: keys.jobSearchRuns(userId),
    queryFn: ({ pageParam }) => getJobSearchRuns(pageParam, SEARCH_RUNS_PAGE_SIZE),
    initialPageParam: 0,
    // A full page may have more after it; a short one is the last.
    getNextPageParam: (lastPage, _pages, lastPageParam) =>
      lastPage.length === SEARCH_RUNS_PAGE_SIZE ? lastPageParam + 1 : undefined,
  })
}

/** What one run found for each company. Only asked for once the run is opened (enabled). */
export function useJobSearchRun(runId: string, enabled: boolean) {
  const userId = useUserId()
  return useQuery({
    queryKey: keys.jobSearchRun(userId, runId),
    queryFn: () => getJobSearchRun(runId),
    enabled,
    // A finished run never changes.
    staleTime: Infinity,
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
