import { useJobSearchRun } from '../api/hooks'
import type { CompanySearchSummary, JobSearchRunSummary } from '../api/types'

type Props = {
  run: JobSearchRunSummary
}

const numberCell = 'px-2 py-2 text-right tabular-nums sm:px-3'

/** Inside an opened row of the search history: what the run found for each company, from each source. */
export function RunDetails({ run }: Props) {
  const detail = useJobSearchRun(run.runId, true)

  if (detail.isPending) {
    return (
      <p role="status" className="flex items-center gap-2 text-sm text-slate-600">
        <span className="h-4 w-4 animate-spin rounded-full border-2 border-indigo-200 border-t-indigo-600" />
        Loading what this search found…
      </p>
    )
  }

  if (detail.isError) {
    return (
      <div className="flex flex-wrap items-center gap-3 text-sm">
        <p className="text-red-700">We couldn’t load this search’s details. {detail.error.message}</p>
        <button
          type="button"
          onClick={() => void detail.refetch()}
          disabled={detail.isFetching}
          className="rounded-md border border-red-300 bg-white px-3 py-1 font-medium text-red-700 hover:bg-red-100 disabled:opacity-60"
        >
          {detail.isFetching ? 'Retrying…' : 'Try again'}
        </button>
      </div>
    )
  }

  const { companies } = detail.data
  if (companies.length === 0) {
    return <p className="text-sm text-slate-500">{noCompaniesText(run)}</p>
  }

  return (
    <div className="space-y-4">
      {companies.map((company) => (
        <CompanyResults key={`${company.companyName}-${company.searchedAt}`} company={company} />
      ))}
    </div>
  )
}

function CompanyResults({ company }: { company: CompanySearchSummary }) {
  return (
    <section aria-label={company.companyName} className="rounded-lg border border-slate-200 bg-white">
      <h3 className="border-b border-slate-200 px-3 py-2 text-sm font-semibold text-slate-800">
        {company.companyName}
      </h3>
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="text-xs text-slate-500">
            <tr>
              <th scope="col" className="px-2 py-2 text-left font-medium sm:px-3">
                Source
              </th>
              <th scope="col" className="px-2 py-2 text-right font-medium sm:px-3">
                Found
              </th>
              <th scope="col" className="px-2 py-2 text-right font-medium sm:px-3">
                New
              </th>
              <th scope="col" className="px-2 py-2 text-right font-medium sm:px-3">
                Already saved
              </th>
              <th scope="col" className="px-2 py-2 text-right font-medium sm:px-3">
                Filtered out
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {company.sources.map((source) => (
              <tr key={source.source}>
                <th scope="row" className="px-2 py-2 text-left font-normal text-slate-700 sm:px-3">
                  {source.source}
                </th>
                {source.status === 'FAILED' ? (
                  <td colSpan={4} className="px-2 py-2 text-right text-red-700 sm:px-3">
                    Couldn’t be searched this time
                  </td>
                ) : (
                  <>
                    <td className={numberCell}>{source.jobsFound}</td>
                    <td className={`${numberCell} ${source.newJobs > 0 ? 'font-semibold text-emerald-700' : ''}`}>
                      {source.newJobs}
                    </td>
                    <td className={numberCell}>{source.duplicateJobs}</td>
                    <td className={numberCell}>{source.filteredOutJobs}</td>
                  </>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}

function noCompaniesText(run: JobSearchRunSummary): string {
  switch (run.status) {
    case 'SKIPPED':
      return 'Your profile had no target companies to search.'
    case 'FAILED':
      return 'This search failed before it reached any of your companies.'
    default:
      return 'No companies were recorded for this search.'
  }
}
