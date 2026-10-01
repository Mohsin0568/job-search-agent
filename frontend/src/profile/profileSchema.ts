import { z } from 'zod'
import {
  DEFAULT_RECENCY_WINDOW_DAYS,
  MAX_COMPANY_PREFERENCES,
  MAX_SKILLS,
  type CandidateProfile,
} from '../api/types'

// Limits mirror CandidateProfileDto on the backend.
export const profileSchema = z.object({
  desiredRole: z.string().trim().min(1, 'Enter the role you’re looking for').max(200, 'Keep it under 200 characters'),
  skills: z.array(z.string().trim().min(1).max(100)).max(MAX_SKILLS, `Add at most ${MAX_SKILLS} skills`),
  currentJobDescription: z.string().trim().max(10_000, 'Keep it under 10,000 characters'),
  companyPreferences: z
    .array(z.string().trim().min(1).max(200))
    .min(1, 'Add at least one company to search')
    .max(MAX_COMPANY_PREFERENCES, `Add at most ${MAX_COMPANY_PREFERENCES} companies`),
  recencyWindowDays: z.number().int().min(1).max(365),
})

export type ProfileFormValues = z.infer<typeof profileSchema>

export const RECENCY_OPTIONS = [3, 7, 14, 30, 60, 90]

export function toFormValues(profile: CandidateProfile | null | undefined): ProfileFormValues {
  return {
    desiredRole: profile?.desiredRole ?? '',
    skills: profile?.skills ?? [],
    currentJobDescription: profile?.currentJobDescription ?? '',
    companyPreferences: profile?.companyPreferences ?? [],
    recencyWindowDays: profile?.recencyWindowDays ?? DEFAULT_RECENCY_WINDOW_DAYS,
  }
}

export function toProfile(values: ProfileFormValues): CandidateProfile {
  return {
    ...values,
    currentJobDescription: values.currentJobDescription || null,
  }
}
