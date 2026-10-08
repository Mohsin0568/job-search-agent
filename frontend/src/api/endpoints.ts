import { ApiError, apiFetch } from './client'
import type { CandidateProfile, JobSearchResult, Suggestion, SuggestionSource } from './types'

/** Returns null when the user hasn't set up a profile yet (backend responds 404). */
export async function getProfile(): Promise<CandidateProfile | null> {
  try {
    return await apiFetch<CandidateProfile>('/api/profile')
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null
    throw error
  }
}

export function saveProfile(profile: CandidateProfile): Promise<CandidateProfile> {
  return apiFetch<CandidateProfile>('/api/profile', {
    method: 'PUT',
    body: JSON.stringify(profile),
  })
}

/** Results are produced by the backend's daily job search; there is no way to start one from here. */
export function getJobResults(page = 0, size = 50): Promise<JobSearchResult[]> {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  return apiFetch<JobSearchResult[]>(`/api/jobs/results?${params}`)
}

/** Known companies or skills matching what the user has typed so far, e.g. "del" -> Deliveroo. */
export function suggestNames(source: SuggestionSource, query: string, signal?: AbortSignal): Promise<Suggestion[]> {
  const params = new URLSearchParams({ q: query })
  return apiFetch<Suggestion[]>(`/api/${source}/suggest?${params}`, { signal })
}
