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

// Backend limits (CandidateProfileDto) - keep in sync.
export const MAX_COMPANY_PREFERENCES = 10
export const MAX_SKILLS = 50
export const DEFAULT_RECENCY_WINDOW_DAYS = 7
