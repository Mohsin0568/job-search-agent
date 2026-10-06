import { describe, expect, it } from 'vitest'
import { result } from '../test/fixtures'
import { companiesWithoutResults, groupByCompany } from './groupByCompany'

const names = (groups: ReturnType<typeof groupByCompany>) => groups.map((group) => group.companyName)
const ids = (group: ReturnType<typeof groupByCompany>[number]) => group.results.map((each) => each.id)

describe('groupByCompany', () => {
  it('returns nothing for no results', () => {
    expect(groupByCompany([])).toEqual([])
  })

  it('groups names that differ only by case or surrounding spaces, keeping the first spelling', () => {
    const groups = groupByCompany([result('a', 'Acme Corp'), result('b', ' acme corp '), result('c', 'ACME CORP')])

    expect(names(groups)).toEqual(['Acme Corp'])
    expect(ids(groups[0])).toHaveLength(3)
  })

  it('puts preferred companies first in the preferred order, then the rest alphabetically', () => {
    const groups = groupByCompany(
      [result('a', 'Zeta'), result('b', 'Acme Corp'), result('c', 'Globex'), result('d', 'Beta')],
      ['globex', 'Acme Corp'],
    )

    expect(names(groups)).toEqual(['Globex', 'Acme Corp', 'Beta', 'Zeta'])
  })

  it('sorts alphabetically when there are no preferences', () => {
    expect(names(groupByCompany([result('a', 'Globex'), result('b', 'Acme Corp')]))).toEqual(['Acme Corp', 'Globex'])
  })

  it('lists the best match first within a company, with unscored jobs last', () => {
    const groups = groupByCompany([
      result('unscored', 'Acme Corp', { atsScore: null }),
      result('fair', 'Acme Corp', { atsScore: 60 }),
      result('strong', 'Acme Corp', { atsScore: 95 }),
      result('zero', 'Acme Corp', { atsScore: 0 }),
    ])

    expect(ids(groups[0])).toEqual(['strong', 'fair', 'zero', 'unscored'])
  })

  it('breaks a tie on score by the most recently posted, with undated jobs last', () => {
    const groups = groupByCompany([
      result('undated', 'Acme Corp', { atsScore: 80, datePosted: 'Not specified' }),
      result('older', 'Acme Corp', { atsScore: 80, datePosted: '01 Aug 2026' }),
      result('newer', 'Acme Corp', { atsScore: 80, datePosted: '15 Aug 2026' }),
    ])

    expect(ids(groups[0])).toEqual(['newer', 'older', 'undated'])
  })

  it('does not reorder the results it was given', () => {
    const results = [result('low', 'Acme Corp', { atsScore: 10 }), result('high', 'Acme Corp', { atsScore: 90 })]

    groupByCompany(results)

    expect(results.map((each) => each.id)).toEqual(['low', 'high'])
  })
})

describe('companiesWithoutResults', () => {
  it('lists preferred companies that have no group, ignoring case', () => {
    const groups = groupByCompany([result('a', 'acme corp')])

    expect(companiesWithoutResults(groups, ['Acme Corp', 'Globex', 'Initech'])).toEqual(['Globex', 'Initech'])
  })

  it('is empty when every preferred company has results', () => {
    const groups = groupByCompany([result('a', 'Acme Corp'), result('b', 'Globex')])

    expect(companiesWithoutResults(groups, ['Acme Corp', 'Globex'])).toEqual([])
  })
})
