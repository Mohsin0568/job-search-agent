import { describe, expect, it } from 'vitest'
import { formatFileSize, formatUploadedAt } from './format'

describe('formatFileSize', () => {
  it.each([
    [0, '0 B'],
    [512, '512 B'],
    [1024, '1 KB'],
    [870_400, '850 KB'],
    [1024 * 1024, '1.0 MB'],
    [1_258_291, '1.2 MB'],
    [5 * 1024 * 1024, '5.0 MB'],
  ])('shows %i bytes as %s', (bytes, expected) => {
    expect(formatFileSize(bytes)).toBe(expected)
  })
})

describe('formatUploadedAt', () => {
  it('shows the day, month and year', () => {
    const formatted = formatUploadedAt('2026-10-12T09:00:00Z')

    expect(formatted).toContain('2026')
    expect(formatted).toContain('12')
    expect(formatted).toMatch(/Oct/)
  })

  it('leaves a value it cannot read as it is', () => {
    expect(formatUploadedAt('not a date')).toBe('not a date')
  })
})
