package com.systa.model;

import java.time.Instant;
import java.util.List;

/** One row of the user's search history: a run, and what it found across all of its companies. */
public record JobSearchRunSummaryDto(
        String runId,
        String runDate,
        Instant startedAt,
        Instant finishedAt,
        UserJobSearchRun.Status status,
        int companiesSearched,
        int newJobs,
        int duplicateJobs,
        int filteredOutJobs,
        int sourcesFailed
) {

    public static JobSearchRunSummaryDto from(final JobSearchRun run,
                                              final List<JobSearchRunDetailDto.CompanyResult> companies) {
        return new JobSearchRunSummaryDto(run.id(), run.runDate(), run.startedAt(), run.finishedAt(), run.status(),
                companies.size(),
                companies.stream().mapToInt(JobSearchRunDetailDto.CompanyResult::newJobs).sum(),
                companies.stream().mapToInt(JobSearchRunDetailDto.CompanyResult::duplicateJobs).sum(),
                companies.stream().mapToInt(JobSearchRunDetailDto.CompanyResult::filteredOutJobs).sum(),
                companies.stream().mapToInt(JobSearchRunDetailDto.CompanyResult::sourcesFailed).sum());
    }
}
