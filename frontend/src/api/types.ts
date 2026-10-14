// Mirrors the Spring Boot DTOs in com.systa.model. The backend allows nulls on every field.

export type CandidateProfile = {
  desiredRole: string | null
  skills: string[] | null
  currentJobDescription: string | null
  companyPreferences: string[] | null
  recencyWindowDays: number | null
}

export type JobListing = {
  jobId: string | null
  jobTitle: string | null
  url: string | null
  location: string | null
  datePosted: string | null
  lastDateForSubmission: string | null
  salaryRange: string | null
  source: string | null
  atsScore: number | null
}

export type JobSearchResult = {
  id: string
  userId: string
  /** ISO local date-time of the search run that found this job. */
  jobRunDateTime: string
  companyName: string
  job: JobListing
}

/** One uploaded version of the user's resume. Every version is kept; the newest is marked latest. */
export type ResumeVersion = {
  version: number
  latest: boolean
  fileName: string
  sizeBytes: number
  /** ISO instant, e.g. "2026-10-12T09:00:00Z". */
  uploadedAt: string
}

/** A short-lived link that downloads a resume straight from storage. */
export type ResumeDownload = {
  url: string
  expiresAt: string
}

/** The layouts a tailored resume can be laid out in (the backend's ResumeTemplate - keep in sync). */
export type ResumeTemplate = 'CLASSIC' | 'MODERN' | 'COMPACT' | 'EVEN' | 'STACK_OVERFLOW' | 'KENDALL'

/** The user's resume tailored to one saved job. Tailoring runs in the background on the server. */
export type TailoredResume = {
  /** The id of the JobSearchResult it is tailored to. */
  jobResultId: string
  status: 'PENDING' | 'READY' | 'FAILED'
  /** The version of the user's resume it was (or is being) tailored from. */
  resumeVersion: number
  template: ResumeTemplate
  /** Why it failed, in words for the user. Null unless it failed. */
  failureReason: string | null
  /** ISO instants. completedAt is null while pending. */
  requestedAt: string
  completedAt: string | null
}

/** Which seeded list to autocomplete from; also the path segment of its endpoint. */
export type SuggestionSource = 'companies' | 'skills'

export type Suggestion = {
  name: string
}

// Backend limits (CandidateProfileDto) - keep in sync.
export const MAX_COMPANY_PREFERENCES = 10
export const MAX_SKILLS = 50
export const DEFAULT_RECENCY_WINDOW_DAYS = 7
// Backend limit (spring.servlet.multipart.max-file-size) - keep in sync.
export const MAX_RESUME_SIZE_BYTES = 5 * 1024 * 1024
