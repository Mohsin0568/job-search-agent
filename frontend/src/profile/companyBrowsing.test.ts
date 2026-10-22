import { describe, expect, it } from 'vitest'
import type { KnownCompany } from '../api/types'
import { filterCompanies, groupBySector, initiallyTicked, sameCompany, withTicked } from './companyBrowsing'

const KNOWN: KnownCompany[] = [
  { name: 'Monzo', sector: 'Fintech' },
  { name: 'eBay', sector: 'Retail & E-commerce' },
  { name: 'Barclays', sector: 'Banking' },
  { name: 'Lloyds Banking Group', sector: 'Banking' },
  { name: 'Acme', sector: null },
  { name: 'Wise', sector: 'Fintech' },
]

describe('sameCompany', () => {
  it('ignores case and stray spaces', () => {
    expect(sameCompany(' monzo', 'Monzo ')).toBe(true)
    expect(sameCompany('Monzo', 'Monzo Bank')).toBe(false)
  })
})

describe('filterCompanies', () => {
  it('keeps the companies whose name contains the query, A-Z whatever the case', () => {
    expect(filterCompanies(KNOWN, ' BANK ').map((company) => company.name)).toEqual(['Lloyds Banking Group'])
    expect(filterCompanies(KNOWN, '').map((company) => company.name)).toEqual([
      'Acme',
      'Barclays',
      'eBay',
      'Lloyds Banking Group',
      'Monzo',
      'Wise',
    ])
  })
})

describe('groupBySector', () => {
  it('lists sectors A-Z with Other last, each sector’s companies A-Z', () => {
    expect(groupBySector(KNOWN).map(({ sector, companies }) => [sector, companies.map((company) => company.name)])).toEqual([
      ['Banking', ['Barclays', 'Lloyds Banking Group']],
      ['Fintech', ['Monzo', 'Wise']],
      ['Retail & E-commerce', ['eBay']],
      ['Other', ['Acme']],
    ])
  })
})

describe('initiallyTicked', () => {
  it('ticks the known companies already in the field, by their listed names, once each', () => {
    expect(initiallyTicked(['wise', 'Not Listed Ltd', 'Monzo', 'WISE'], KNOWN)).toEqual(['Wise', 'Monzo'])
  })
})

describe('withTicked', () => {
  it('keeps the field’s order and spelling, drops the unticked, then adds the newly ticked in order', () => {
    const current = ['wise', 'Not Listed Ltd', 'Monzo']

    expect(withTicked(current, KNOWN, ['Monzo', 'Barclays', 'eBay'])).toEqual([
      'Not Listed Ltd',
      'Monzo',
      'Barclays',
      'eBay',
    ])
  })

  it('leaves the field as it was when nothing changes', () => {
    const current = ['wise', 'Not Listed Ltd']

    expect(withTicked(current, KNOWN, initiallyTicked(current, KNOWN))).toEqual(current)
  })
})
