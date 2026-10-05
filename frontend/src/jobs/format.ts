// Job fields are filled in by the LLM from scraped pages (see job_search_system_prompt.txt):
// missing strings come back as "Not specified" and dates as "dd MMM yyyy".

const NOT_SPECIFIED = 'not specified'

/** The value, or null when it's empty or the LLM's "Not specified" placeholder. */
export function specified(value: string | null | undefined): string | null {
  const trimmed = value?.trim()
  if (!trimmed || trimmed.toLowerCase() === NOT_SPECIFIED) return null
  return trimmed
}

const MONTHS = ['jan', 'feb', 'mar', 'apr', 'may', 'jun', 'jul', 'aug', 'sep', 'oct', 'nov', 'dec']

/** Parses "dd MMM yyyy" (e.g. "07 Aug 2026") to a timestamp, or null if it isn't in that format. */
export function parseJobDate(value: string | null | undefined): number | null {
  const match = specified(value)?.match(/^(\d{1,2}) ([A-Za-z]{3})[a-z]* (\d{4})$/)
  if (!match) return null
  const month = MONTHS.indexOf(match[2].toLowerCase())
  if (month < 0) return null
  return new Date(Number(match[3]), month, Number(match[1])).getTime()
}

/**
 * Only http(s) links are rendered: URLs come from scraped pages, and a "javascript:" URL
 * in an href would run in our origin, where the Cognito tokens live.
 */
export function safeUrl(value: string | null | undefined): string | null {
  const candidate = specified(value)
  if (!candidate) return null
  try {
    const url = new URL(candidate)
    return url.protocol === 'https:' || url.protocol === 'http:' ? url.href : null
  } catch {
    return null
  }
}

export type AtsTone = 'strong' | 'fair' | 'weak'

// The system prompt defines 80+ as a strong match.
export function atsTone(score: number): AtsTone {
  if (score >= 80) return 'strong'
  if (score >= 60) return 'fair'
  return 'weak'
}

/** "Updated 2 Oct 2026, 09:15" from the backend's LocalDateTime (no zone, server-local time). */
export function formatRunDateTime(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}
