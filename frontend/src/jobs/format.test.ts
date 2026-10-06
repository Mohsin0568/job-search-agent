import { describe, expect, it } from 'vitest'
import { atsTone, formatRunDateTime, parseJobDate, safeUrl, specified } from './format'

describe('specified', () => {
  it.each([null, undefined, '', '   ', 'Not specified', ' NOT SPECIFIED '])('treats %j as missing', (value) => {
    expect(specified(value)).toBeNull()
  })

  it('returns the trimmed value otherwise', () => {
    expect(specified('  London, UK ')).toBe('London, UK')
  })
})

describe('parseJobDate', () => {
  it('parses "dd MMM yyyy"', () => {
    expect(parseJobDate('07 Aug 2026')).toBe(new Date(2026, 7, 7).getTime())
    expect(parseJobDate('7 aug 2026')).toBe(new Date(2026, 7, 7).getTime())
  })

  it('accepts a full month name', () => {
    expect(parseJobDate('07 August 2026')).toBe(new Date(2026, 7, 7).getTime())
  })

  it.each(['2026-08-07', '07/08/2026', '07 Xyz 2026', 'Aug 2026', 'Not specified', '', null, undefined])(
    'returns null for %j',
    (value) => {
      expect(parseJobDate(value)).toBeNull()
    },
  )

  it('orders dates chronologically', () => {
    expect(parseJobDate('01 Sep 2026')!).toBeGreaterThan(parseJobDate('30 Aug 2026')!)
  })
})

describe('safeUrl', () => {
  it('keeps http and https links', () => {
    expect(safeUrl('https://example.com/jobs/1?ref=a')).toBe('https://example.com/jobs/1?ref=a')
    expect(safeUrl(' http://example.com/jobs/1 ')).toBe('http://example.com/jobs/1')
  })

  it.each([
    'javascript:alert(1)',
    ' JavaScript:alert(document.cookie)',
    'data:text/html,<script>alert(1)</script>',
    'vbscript:msgbox(1)',
    'file:///etc/passwd',
    'mailto:jobs@example.com',
  ])('rejects the non-web URL %j', (value) => {
    expect(safeUrl(value)).toBeNull()
  })

  it.each(['/jobs/1', 'example.com/jobs/1', 'not a url', 'Not specified', '', null, undefined])(
    'rejects %j, which is not an absolute URL',
    (value) => {
      expect(safeUrl(value)).toBeNull()
    },
  )
})

describe('atsTone', () => {
  it.each([
    [100, 'strong'],
    [80, 'strong'],
    [79, 'fair'],
    [60, 'fair'],
    [59, 'weak'],
    [0, 'weak'],
  ])('rates %i as %s', (score, tone) => {
    expect(atsTone(score)).toBe(tone)
  })
})

describe('formatRunDateTime', () => {
  it('formats a local date-time for display', () => {
    const formatted = formatRunDateTime('2026-10-02T09:15:00')

    expect(formatted).not.toBe('2026-10-02T09:15:00')
    expect(formatted).toContain('2026')
    expect(formatted).toContain('15')
  })

  it('returns the input when it is not a date', () => {
    expect(formatRunDateTime('yesterday')).toBe('yesterday')
  })
})
