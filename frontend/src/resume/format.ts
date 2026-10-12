/** "850 KB", "1.2 MB" - enough precision to recognise a file, not to audit it. */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

/** "12 Oct 2026" in the user's locale, from the backend's ISO instant. */
export function formatUploadedAt(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
}
