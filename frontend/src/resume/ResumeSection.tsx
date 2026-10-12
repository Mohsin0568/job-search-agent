import { useId, useState, type ChangeEvent } from 'react'
import { useResumeDownload, useResumeVersions, useUploadResume } from '../api/hooks'
import { MAX_RESUME_SIZE_BYTES, type ResumeVersion } from '../api/types'
import { Alert } from '../components/form/Alert'
import { startDownload } from './download'
import { formatFileSize, formatUploadedAt } from './format'

const secondaryButtonClass =
  'rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 shadow-sm transition hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-indigo-200 disabled:cursor-not-allowed disabled:opacity-60'

/** Checked here so an unusable file is turned away before it is uploaded; the backend checks again. */
function problemWith(file: File): string | undefined {
  if (file.type !== 'application/pdf' && !file.name.toLowerCase().endsWith('.pdf')) {
    return 'Your resume must be a PDF file.'
  }
  if (file.size === 0) return 'That file is empty.'
  if (file.size > MAX_RESUME_SIZE_BYTES) return 'Your resume must be 5MB or smaller.'
  return undefined
}

function describe(resume: ResumeVersion): string {
  return `Version ${resume.version} · ${formatFileSize(resume.sizeBytes)} · uploaded ${formatUploadedAt(resume.uploadedAt)}`
}

/**
 * The user's resume: upload one, replace it, and download any version. It saves on its own, as soon
 * as a file is picked - separately from the profile form it sits beside.
 */
export function ResumeSection() {
  const headingId = useId()
  const { data: versions, isPending, isError, error, refetch, isFetching } = useResumeVersions()
  const upload = useUploadResume()
  const download = useResumeDownload()
  const [problem, setProblem] = useState<string>()
  const [uploadedVersion, setUploadedVersion] = useState<number>()

  const latest = versions?.find((resume) => resume.latest) ?? versions?.[0]
  const earlier = versions?.filter((resume) => resume !== latest) ?? []

  const onFilePicked = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    // Cleared so picking the same file again (after fixing it, say) still raises a change event.
    event.target.value = ''
    if (!file) return

    setUploadedVersion(undefined)
    setProblem(problemWith(file))
    if (problemWith(file)) return

    try {
      const uploaded = await upload.mutateAsync(file)
      setUploadedVersion(uploaded.version)
    } catch (uploadError) {
      // 400s carry the backend's reason (not a PDF, no readable text, ...).
      setProblem(uploadError instanceof Error ? uploadError.message : 'Couldn’t upload your resume. Please try again.')
    }
  }

  const onDownload = async (resume: ResumeVersion) => {
    setProblem(undefined)
    try {
      const link = await download.mutateAsync(resume.version)
      startDownload(link.url)
    } catch (downloadError) {
      setProblem(downloadError instanceof Error ? downloadError.message : 'Couldn’t download your resume. Please try again.')
    }
  }

  return (
    <section aria-labelledby={headingId} className="space-y-4">
      <div>
        <h2 id={headingId} className="text-lg font-semibold">
          Your resume
        </h2>
        <p className="mt-1 text-sm text-slate-500">PDF, up to 5MB. You can replace it at any time; earlier versions are kept.</p>
      </div>

      {problem && <Alert variant="error">{problem}</Alert>}
      {uploadedVersion !== undefined && !problem && (
        <Alert variant="success">Your resume has been uploaded as version {uploadedVersion}.</Alert>
      )}

      {isPending ? (
        <p className="text-sm text-slate-500">Loading your resume…</p>
      ) : isError ? (
        <div className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800" role="alert">
          <p>We couldn’t load your resume. {error.message}</p>
          <button type="button" onClick={() => void refetch()} disabled={isFetching} className={`mt-2 ${secondaryButtonClass}`}>
            {isFetching ? 'Retrying…' : 'Try again'}
          </button>
        </div>
      ) : (
        <>
          {latest ? (
            <div className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-slate-200 bg-slate-50 p-3">
              <div className="min-w-0">
                <p className="truncate text-sm font-medium text-slate-900" title={latest.fileName}>
                  {latest.fileName}
                </p>
                <p className="text-xs text-slate-500">{describe(latest)}</p>
              </div>
              <button
                type="button"
                onClick={() => void onDownload(latest)}
                disabled={download.isPending}
                aria-label="Download your current resume"
                className={secondaryButtonClass}
              >
                Download
              </button>
            </div>
          ) : (
            <p className="text-sm text-slate-600">You haven’t uploaded a resume yet.</p>
          )}

          {/* A label styled as a button: clicking it opens the file picker of the hidden input inside. */}
          <label
            className={`inline-flex cursor-pointer items-center gap-2 rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-sm transition hover:bg-indigo-500 focus-within:ring-2 focus-within:ring-indigo-300 ${
              upload.isPending ? 'cursor-not-allowed opacity-60' : ''
            }`}
          >
            {upload.isPending && (
              <span className="h-4 w-4 animate-spin rounded-full border-2 border-white/40 border-t-white" />
            )}
            {upload.isPending ? 'Uploading…' : latest ? 'Upload a new version' : 'Upload your resume'}
            <input
              type="file"
              accept="application/pdf,.pdf"
              className="sr-only"
              disabled={upload.isPending}
              onChange={(event) => void onFilePicked(event)}
            />
          </label>

          {earlier.length > 0 && (
            <div>
              <h3 className="text-sm font-medium text-slate-700">Earlier versions</h3>
              <ul className="mt-2 divide-y divide-slate-200 rounded-md border border-slate-200">
                {earlier.map((resume) => (
                  <li key={resume.version} className="flex flex-wrap items-center justify-between gap-3 p-3">
                    <div className="min-w-0">
                      <p className="truncate text-sm text-slate-800" title={resume.fileName}>
                        {resume.fileName}
                      </p>
                      <p className="text-xs text-slate-500">{describe(resume)}</p>
                    </div>
                    <button
                      type="button"
                      onClick={() => void onDownload(resume)}
                      disabled={download.isPending}
                      aria-label={`Download version ${resume.version}`}
                      className={secondaryButtonClass}
                    >
                      Download
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </>
      )}
    </section>
  )
}
