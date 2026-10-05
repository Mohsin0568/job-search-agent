import type { JobSearchResult } from '../api/types'
import { parseJobDate } from './format'

export type CompanyGroup = {
  companyName: string
  results: JobSearchResult[]
}

// Best matches first, then most recently posted.
function compareResults(a: JobSearchResult, b: JobSearchResult): number {
  const scoreDiff = (b.job.atsScore ?? -1) - (a.job.atsScore ?? -1)
  if (scoreDiff !== 0) return scoreDiff
  return (parseJobDate(b.job.datePosted) ?? 0) - (parseJobDate(a.job.datePosted) ?? 0)
}

const normalise = (name: string) => name.trim().toLowerCase()

/**
 * Groups results by company (case-insensitively, since the name comes back from the LLM).
 * Companies follow the user's preference order; any others come after, alphabetically.
 */
export function groupByCompany(results: JobSearchResult[], preferredOrder: string[] = []): CompanyGroup[] {
  const groups = new Map<string, CompanyGroup>()
  for (const result of results) {
    const key = normalise(result.companyName)
    const group = groups.get(key)
    if (group) {
      group.results.push(result)
    } else {
      groups.set(key, { companyName: result.companyName.trim(), results: [result] })
    }
  }

  const rank = new Map(preferredOrder.map((name, index) => [normalise(name), index]))
  return [...groups.entries()]
    .sort(([keyA, a], [keyB, b]) => {
      const rankA = rank.get(keyA) ?? Number.MAX_SAFE_INTEGER
      const rankB = rank.get(keyB) ?? Number.MAX_SAFE_INTEGER
      return rankA !== rankB ? rankA - rankB : a.companyName.localeCompare(b.companyName)
    })
    .map(([, group]) => ({ ...group, results: [...group.results].sort(compareResults) }))
}

/** Preferred companies that have no results, for the "nothing found at…" note. */
export function companiesWithoutResults(groups: CompanyGroup[], preferred: string[]): string[] {
  const found = new Set(groups.map((group) => normalise(group.companyName)))
  return preferred.filter((name) => !found.has(normalise(name)))
}
