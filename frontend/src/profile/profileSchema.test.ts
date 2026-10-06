import { describe, expect, it } from 'vitest'
import { MAX_COMPANY_PREFERENCES, MAX_SKILLS } from '../api/types'
import { profileSchema, toFormValues, toProfile, type ProfileFormValues } from './profileSchema'

const valid: ProfileFormValues = {
  desiredRole: 'Senior Java Developer',
  skills: ['Java'],
  currentJobDescription: '',
  companyPreferences: ['Acme Corp'],
  recencyWindowDays: 7,
}

function messages(values: unknown): string[] {
  const parsed = profileSchema.safeParse(values)
  return parsed.success ? [] : parsed.error.issues.map((issue) => issue.message)
}

const items = (count: number) => Array.from({ length: count }, (_, i) => `item ${i}`)

describe('profileSchema', () => {
  it('accepts a complete profile', () => {
    expect(profileSchema.safeParse(valid).success).toBe(true)
  })

  it('accepts a profile with no skills and no current role', () => {
    expect(profileSchema.safeParse({ ...valid, skills: [], currentJobDescription: '' }).success).toBe(true)
  })

  it('trims text fields', () => {
    const parsed = profileSchema.parse({ ...valid, desiredRole: '  Java Developer  ', companyPreferences: [' Acme '] })

    expect(parsed.desiredRole).toBe('Java Developer')
    expect(parsed.companyPreferences).toEqual(['Acme'])
  })

  it('requires a role', () => {
    expect(messages({ ...valid, desiredRole: '   ' })).toEqual(['Enter the role you’re looking for'])
  })

  it('limits the role to 200 characters', () => {
    expect(profileSchema.safeParse({ ...valid, desiredRole: 'x'.repeat(200) }).success).toBe(true)
    expect(messages({ ...valid, desiredRole: 'x'.repeat(201) })).toEqual(['Keep it under 200 characters'])
  })

  it('requires at least one company', () => {
    expect(messages({ ...valid, companyPreferences: [] })).toEqual(['Add at least one company to search'])
  })

  it('limits the number of companies', () => {
    expect(profileSchema.safeParse({ ...valid, companyPreferences: items(MAX_COMPANY_PREFERENCES) }).success).toBe(true)
    expect(messages({ ...valid, companyPreferences: items(MAX_COMPANY_PREFERENCES + 1) })).toEqual([
      `Add at most ${MAX_COMPANY_PREFERENCES} companies`,
    ])
  })

  it('limits the number of skills', () => {
    expect(profileSchema.safeParse({ ...valid, skills: items(MAX_SKILLS) }).success).toBe(true)
    expect(messages({ ...valid, skills: items(MAX_SKILLS + 1) })).toEqual([`Add at most ${MAX_SKILLS} skills`])
  })

  it('limits the current role description to 10,000 characters', () => {
    expect(messages({ ...valid, currentJobDescription: 'x'.repeat(10_001) })).toEqual([
      'Keep it under 10,000 characters',
    ])
  })

  it.each([0, 366, 7.5, Number.NaN])('rejects a recency window of %d days', (days) => {
    expect(profileSchema.safeParse({ ...valid, recencyWindowDays: days }).success).toBe(false)
  })
})

describe('toFormValues', () => {
  it('gives an empty form with the default recency window when there is no profile', () => {
    expect(toFormValues(null)).toEqual({
      desiredRole: '',
      skills: [],
      currentJobDescription: '',
      companyPreferences: [],
      recencyWindowDays: 7,
    })
    expect(toFormValues(undefined)).toEqual(toFormValues(null))
  })

  it('replaces nulls from the backend with empty values', () => {
    expect(
      toFormValues({
        desiredRole: 'Java Developer',
        skills: null,
        currentJobDescription: null,
        companyPreferences: ['Acme Corp'],
        recencyWindowDays: null,
      }),
    ).toEqual({
      desiredRole: 'Java Developer',
      skills: [],
      currentJobDescription: '',
      companyPreferences: ['Acme Corp'],
      recencyWindowDays: 7,
    })
  })
})

describe('toProfile', () => {
  it('sends an empty current role as null', () => {
    expect(toProfile(valid).currentJobDescription).toBeNull()
  })

  it('keeps every other field as entered', () => {
    expect(toProfile({ ...valid, currentJobDescription: 'Builds APIs' })).toEqual({
      ...valid,
      currentJobDescription: 'Builds APIs',
    })
  })
})
