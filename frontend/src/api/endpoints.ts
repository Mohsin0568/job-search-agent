import { env } from '../config/env'
import { ApiError, apiFetch } from './client'
import type {
  CandidateProfile,
  JobSearchResult,
  JobSearchRunDetail,
  JobSearchRunSummary,
  ResumeDownload,
  ResumeReview,
  ResumeVersion,
  ResumeTemplate,
  TailoredResume,
  TailoringFix,
  Suggestion,
  SuggestionSource,
} from './types'

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

/** Every resume version the user has uploaded, newest first. Empty until they upload one. */
export function getResumeVersions(): Promise<ResumeVersion[]> {
  return apiFetch<ResumeVersion[]>('/api/profile/resume')
}

/** Uploads a PDF as the user's next resume version, which becomes their latest. */
export function uploadResume(file: File): Promise<ResumeVersion> {
  const body = new FormData()
  body.append('file', file)
  return apiFetch<ResumeVersion>('/api/profile/resume', { method: 'POST', body })
}

export function getResumeDownload(version: number): Promise<ResumeDownload> {
  return apiFetch<ResumeDownload>(`/api/profile/resume/${version}/download`)
}

/** Every tailored resume the user has asked for, one per job, in whatever state it is. */
export function getTailoredResumes(): Promise<TailoredResume[]> {
  return apiFetch<TailoredResume[]>('/api/jobs/tailored-resumes')
}

/**
 * Asks for the user's latest resume to be tailored to a job, in the given template and with the given
 * fixes - leaving either out keeps what the job has now. Comes back PENDING while the server works on it;
 * getTailoredResumes shows when it is done. Comes back READY if it already has one - including when only
 * the template changed, which the server does straight away. Fixing anything needs a finished review of
 * the user's latest resume (409 without one).
 */
export function requestTailoredResume(
  jobResultId: string,
  { template, fix }: { template?: ResumeTemplate; fix?: TailoringFix } = {},
): Promise<TailoredResume> {
  return apiFetch<TailoredResume>(`/api/jobs/results/${encodeURIComponent(jobResultId)}/tailored-resume`, {
    method: 'POST',
    body: JSON.stringify({ ...(template && { template }), ...(fix && { fix }) }),
  })
}

/**
 * Where a picture of the template, laid out with a sample resume, can be loaded from. Open to anyone,
 * so it goes straight into an <img> - which couldn't send the user's token.
 */
export function templatePreviewUrl(template: ResumeTemplate): string {
  return `${env.apiBaseUrl}/api/resume-templates/${template}/preview`
}

export function getTailoredResumeDownload(jobResultId: string): Promise<ResumeDownload> {
  return apiFetch<ResumeDownload>(`/api/jobs/results/${encodeURIComponent(jobResultId)}/tailored-resume/download`)
}

/** Every resume review the user has, one per job, in whatever state it is. */
export function getResumeReviews(): Promise<ResumeReview[]> {
  return apiFetch<ResumeReview[]>('/api/jobs/resume-reviews')
}

/**
 * Asks for the user's latest resume to be reviewed against a job. Comes back PENDING while the server
 * works on it; getResumeReviews shows when it is done. Comes back READY if it already has one of that resume.
 */
export function requestResumeReview(jobResultId: string): Promise<ResumeReview> {
  return apiFetch<ResumeReview>(`/api/jobs/results/${encodeURIComponent(jobResultId)}/resume-review`, {
    method: 'POST',
  })
}

/** Results are produced by the backend's daily job search; there is no way to start one from here. */
export function getJobResults(page = 0, size = 50): Promise<JobSearchResult[]> {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  return apiFetch<JobSearchResult[]>(`/api/jobs/results?${params}`)
}

/** One page of the user's search history, newest run first. A page shorter than size is the last. */
export function getJobSearchRuns(page = 0, size = 20): Promise<JobSearchRunSummary[]> {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  return apiFetch<JobSearchRunSummary[]>(`/api/jobs/search-runs?${params}`)
}

/** What one run of the search found for each company, from each source. */
export function getJobSearchRun(runId: string): Promise<JobSearchRunDetail> {
  return apiFetch<JobSearchRunDetail>(`/api/jobs/search-runs/${encodeURIComponent(runId)}`)
}

/** Known companies or skills matching what the user has typed so far, e.g. "del" -> Deliveroo. */
export function suggestNames(source: SuggestionSource, query: string, signal?: AbortSignal): Promise<Suggestion[]> {
  const params = new URLSearchParams({ q: query })
  return apiFetch<Suggestion[]>(`/api/${source}/suggest?${params}`, { signal })
}
