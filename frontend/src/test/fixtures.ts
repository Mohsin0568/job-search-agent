import type {
  CandidateProfile,
  JobListing,
  JobSearchResult,
  ResumeReview,
  ResumeVersion,
  TailoredResume,
} from '../api/types'

export function profile(overrides: Partial<CandidateProfile> = {}): CandidateProfile {
  return {
    desiredRole: 'Senior Java Developer',
    skills: ['Java', 'Spring Boot'],
    currentJobDescription: 'Builds APIs',
    companyPreferences: ['Acme Corp', 'Globex'],
    recencyWindowDays: 14,
    ...overrides,
  }
}

export function resumeVersion(overrides: Partial<ResumeVersion> = {}): ResumeVersion {
  return {
    version: 1,
    latest: true,
    fileName: 'cv.pdf',
    sizeBytes: 2048,
    uploadedAt: '2026-10-12T09:00:00Z',
    ...overrides,
  }
}

export function tailoredResume(overrides: Partial<TailoredResume> = {}): TailoredResume {
  return {
    jobResultId: 'r-1',
    status: 'READY',
    resumeVersion: 3,
    template: 'CLASSIC',
    failureReason: null,
    requestedAt: '2026-10-13T09:00:00Z',
    completedAt: '2026-10-13T09:00:40Z',
    fix: null,
    ...overrides,
  }
}

export function resumeReview(overrides: Partial<ResumeReview> = {}): ResumeReview {
  return {
    jobResultId: 'r-1',
    status: 'READY',
    resumeVersion: 3,
    resumeAtsScore: 64,
    missingKeywords: ['Kubernetes', 'Terraform'],
    redFlags: [{ issue: 'No measurable results', explanation: 'Bullets list duties, not outcomes.' }],
    failureReason: null,
    requestedAt: '2026-10-15T09:00:00Z',
    reviewedAt: '2026-10-15T09:00:40Z',
    ...overrides,
  }
}

export function job(overrides: Partial<JobListing> = {}): JobListing {
  return {
    jobId: 'J-1',
    jobTitle: 'Backend Engineer',
    url: 'https://example.com/jobs/J-1',
    location: 'London, UK',
    datePosted: '10 Aug 2026',
    lastDateForSubmission: '20 Aug 2026',
    salaryRange: '£50,000 - £60,000',
    source: 'LinkedIn',
    atsScore: 85,
    ...overrides,
  }
}

export function result(id: string, companyName: string, jobOverrides: Partial<JobListing> = {}): JobSearchResult {
  return {
    id,
    userId: 'user-1',
    jobRunDateTime: '2026-08-18T12:00:00',
    companyName,
    job: job({ jobId: id, ...jobOverrides }),
  }
}
