/**
 * Hands a download link to the browser. The link answers with "Content-Disposition: attachment",
 * so the file is saved and the page stays where it is.
 */
export function startDownload(url: string): void {
  const link = document.createElement('a')
  link.href = url
  link.rel = 'noopener'
  document.body.append(link)
  link.click()
  link.remove()
}
