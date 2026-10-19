import { useJobSearchRuns } from '../api/hooks'
import { SearchRunRows } from '../history/SearchRunRows'

const COLUMNS = ['Search', 'Status', 'Companies', 'New jobs', 'Already saved', 'Filtered out']

/** Every daily search of the user's target companies, newest first; a row opens to show each company. */
export function SearchHistoryPage() {
  const runs = useJobSearchRuns()
  const loaded = runs.data?.pages.flat() ?? []

  return (
    <div>
      <h1 className="text-2xl font-semibold">Search history</h1>
      <p className="mt-1 text-slate-500">
        Every daily search of your target companies, newest first. Open one to see what each source found.
      </p>

      <div className="mt-8">
        {runs.isPending ? (
          <div className="space-y-2" aria-busy="true" aria-label="Loading your search history">
            {Array.from({ length: 4 }, (_, i) => (
              <div key={i} className="h-12 animate-pulse rounded bg-slate-200" />
            ))}
          </div>
        ) : runs.isError ? (
          <div className="rounded-xl border border-red-200 bg-red-50 p-6 text-center">
            <p className="font-medium text-red-800">We couldn’t load your search history</p>
            <p className="mt-1 text-sm text-red-700">{runs.error.message}</p>
            <button
              type="button"
              onClick={() => void runs.refetch()}
              disabled={runs.isFetching}
              className="mt-4 rounded-md border border-red-300 bg-white px-3 py-1.5 text-sm font-medium text-red-700 hover:bg-red-100 disabled:opacity-60"
            >
              {runs.isFetching ? 'Retrying…' : 'Try again'}
            </button>
          </div>
        ) : loaded.length === 0 ? (
          <div className="rounded-xl border border-dashed border-slate-300 bg-white p-10 text-center">
            <p className="font-medium text-slate-800">No searches yet</p>
            <p className="mt-1 text-sm text-slate-500">
              We search your target companies every day. Each search will be listed here once it has run.
            </p>
          </div>
        ) : (
          <>
            <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white">
              <table className="w-full text-sm">
                <caption className="sr-only">Your job searches, newest first</caption>
                <thead className="bg-slate-50 text-xs text-slate-500">
                  <tr>
                    {COLUMNS.map((column, index) => (
                      <th
                        key={column}
                        scope="col"
                        className={`whitespace-nowrap px-3 py-3 font-medium sm:px-4 ${
                          // The count columns are hidden on a phone, as they are in each row.
                          index < 2 ? 'text-left' : 'hidden text-right sm:table-cell'
                        }`}
                      >
                        {column}
                      </th>
                    ))}
                  </tr>
                </thead>
                {loaded.map((run) => (
                  <SearchRunRows key={run.runId} run={run} columns={COLUMNS.length} />
                ))}
              </table>
            </div>
            {runs.hasNextPage && (
              <div className="mt-4 text-center">
                <button
                  type="button"
                  onClick={() => void runs.fetchNextPage()}
                  disabled={runs.isFetchingNextPage}
                  className="rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100 disabled:opacity-60"
                >
                  {runs.isFetchingNextPage ? 'Loading…' : 'Show older searches'}
                </button>
              </div>
            )}
            {runs.isFetchNextPageError && (
              <p role="alert" className="mt-2 text-center text-sm text-red-700">
                We couldn’t load older searches. Please try again.
              </p>
            )}
          </>
        )}
      </div>
    </div>
  )
}
