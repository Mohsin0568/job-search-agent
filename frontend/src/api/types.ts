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

/** Where a saved job stands: on the dashboard (ACTIVE), or marked by the user and moved off it. */
export type JobStatus = 'ACTIVE' | 'APPLIED' | 'NOT_RELEVANT'

/** The longest not-relevant reason the backend keeps (UserJobSearchResult.MAX_NOT_RELEVANT_REASON_LENGTH). */
export const MAX_NOT_RELEVANT_REASON_LENGTH = 500

export type JobSearchResult = {
  id: string
  userId: string
  /** ISO local date-time of the search run that found this job. */
  jobRunDateTime: string
  companyName: string
  job: JobListing
  status: JobStatus
  /** ISO instant the user last changed the status. Null until they do. */
  statusChangedAt: string | null
  /** Why the user doesn't want the job, if they said. Null unless the status is NOT_RELEVANT. */
  notRelevantReason: string | null
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
  /** What it fixes from the job's resume review. Null when it was tailored as is. */
  fix: AppliedFix | null
}

/** Findings from the job's resume review that a tailored resume fixes. */
export type AppliedFix = {
  /** Missing keywords the user confirmed they have, worked into the resume. */
  keywords: string[]
  /** Red flags addressed by rewording what the resume already says. */
  redFlags: RedFlag[]
}

/**
 * What to fix when tailoring (the backend's TailoringFix): the missing keywords the user ticked, and
 * whether to address the red flags. No keywords and no red flags tailors the resume as is.
 */
export type TailoringFix = {
  keywords: string[]
  redFlags: boolean
}

/** Something a hiring manager would hold against the resume, and why. */
export type RedFlag = {
  issue: string
  explanation: string
}

/**
 * A recruiter's-eye review of the user's resume against one saved job (the backend's ResumeReviewDto).
 * Reviewing runs in the background on the server, on request or in the daily job.
 */
export type ResumeReview = {
  /** The id of the JobSearchResult it is for. */
  jobResultId: string
  status: 'PENDING' | 'READY' | 'FAILED'
  /** The version of the user's resume it reviewed (or is reviewing) - always an original upload. */
  resumeVersion: number
  /** 0 to 100. Null until it is ready. */
  resumeAtsScore: number | null
  /** Empty when the resume misses nothing. Null until it is ready. */
  missingKeywords: string[] | null
  /** Empty when there are none. Null until it is ready. */
  redFlags: RedFlag[] | null
  /** Why it failed, in words for the user. Null unless it failed. */
  failureReason: string | null
  /** ISO instants. reviewedAt is null while pending. */
  requestedAt: string
  reviewedAt: string | null
}

/** How one user's part in a daily search went (the backend's UserJobSearchRun.Status). */
export type JobSearchRunStatus =
  /** Every source of every company was searched. */
  | 'SUCCEEDED'
  /** Some sources failed; the rest were searched and their jobs saved. */
  | 'PARTIAL'
  | 'FAILED'
  /** The profile names no companies, so there was nothing to search. */
  | 'SKIPPED'

/** One row of the user's search history (the backend's JobSearchRunSummaryDto): totals across companies. */
export type JobSearchRunSummary = {
  runId: string
  /** The day (UTC, yyyy-MM-dd) the daily search started on. */
  runDate: string
  /** ISO instants. */
  startedAt: string
  finishedAt: string
  status: JobSearchRunStatus
  companiesSearched: number
  /** Jobs saved for the first time. */
  newJobs: number
  /** Jobs found again that the user already had from an earlier search. */
  duplicateJobs: number
  /** Jobs dropped for being too old, or for a date that couldn't be read. */
  filteredOutJobs: number
  sourcesFailed: number
}

/** One run of the search history with what it found for each company (the backend's JobSearchRunDetailDto). */
export type JobSearchRunDetail = {
  run: JobSearchRunSummary
  /** In the order they were searched. */
  companies: CompanySearchSummary[]
}

export type CompanySearchSummary = {
  companyName: string
  searchedAt: string
  newJobs: number
  duplicateJobs: number
  filteredOutJobs: number
  sourcesFailed: number
  sources: SourceSearchSummary[]
}

/** What one source (a job board, or the company's own careers portal) found for a company. */
export type SourceSearchSummary = {
  /** e.g. "LinkedIn", "Company careers portal". */
  source: string
  status: 'SCANNED' | 'FAILED'
  /** jobsFound = filteredOutJobs + newJobs + duplicateJobs. All zero when the source failed. */
  jobsFound: number
  newJobs: number
  duplicateJobs: number
  filteredOutJobs: number
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
