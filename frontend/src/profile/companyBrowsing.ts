import type { KnownCompany } from '../api/types'

/** How the company browser lists companies: one A-Z list, or under a heading per sector. */
export type CompanySort = 'name' | 'sector'

/** Where a company seeded without a sector is listed. */
export const OTHER_SECTOR = 'Other'

/** The same company, whatever the case or stray spaces - "deliveroo " is Deliveroo. */
export function sameCompany(a: string, b: string): boolean {
  return a.trim().toLocaleLowerCase() === b.trim().toLocaleLowerCase()
}

const byName = (a: KnownCompany, b: KnownCompany) => a.name.localeCompare(b.name, undefined, { sensitivity: 'base' })

/** The companies whose name contains the query, A-Z. */
export function filterCompanies(companies: KnownCompany[], query: string): KnownCompany[] {
  const wanted = query.trim().toLocaleLowerCase()
  return companies.filter((company) => company.name.toLocaleLowerCase().includes(wanted)).sort(byName)
}

/** The companies under their sectors: sectors A-Z with Other last, each sector's companies A-Z. */
export function groupBySector(companies: KnownCompany[]): { sector: string; companies: KnownCompany[] }[] {
  const groups = new Map<string, KnownCompany[]>()
  for (const company of companies) {
    const sector = company.sector ?? OTHER_SECTOR
    groups.set(sector, [...(groups.get(sector) ?? []), company])
  }
  return [...groups.entries()]
    .sort(([a], [b]) => (a === OTHER_SECTOR ? 1 : b === OTHER_SECTOR ? -1 : a.localeCompare(b)))
    .map(([sector, list]) => ({ sector, companies: [...list].sort(byName) }))
}

/** The known companies already in the field, by their listed names, in the field's order. */
export function initiallyTicked(current: string[], known: KnownCompany[]): string[] {
  const ticked: string[] = []
  for (const entry of current) {
    const match = known.find((company) => sameCompany(company.name, entry))
    if (match && !ticked.includes(match.name)) ticked.push(match.name)
  }
  return ticked
}

/**
 * The field's companies once browsing is done: what was there, in its order and as it was spelled -
 * including companies the user typed that aren't known - less any known one now unticked, then the newly
 * ticked ones in the order they were ticked.
 */
export function withTicked(current: string[], known: KnownCompany[], ticked: string[]): string[] {
  const kept = current.filter((entry) => {
    const match = known.find((company) => sameCompany(company.name, entry))
    return !match || ticked.includes(match.name)
  })
  const added = ticked.filter((name) => !kept.some((entry) => sameCompany(entry, name)))
  return [...kept, ...added]
}
