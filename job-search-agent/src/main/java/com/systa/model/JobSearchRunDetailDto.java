package com.systa.model;

import com.systa.model.JobSearchAudit.SourceAudit;

import java.time.Instant;
import java.util.List;

/** One run of the user's search history, with what it found for each company from each source. */
public record JobSearchRunDetailDto(JobSearchRunSummaryDto run, List<CompanyResult> companies) {

    public record CompanyResult(
            String companyName,
            Instant searchedAt,
            int newJobs,
            int duplicateJobs,
            int filteredOutJobs,
            int sourcesFailed,
            List<SourceResult> sources
    ) {

        public static CompanyResult from(final JobSearchAudit audit) {
            final List<SourceResult> sources = audit.sources().stream().map(SourceResult::from).toList();
            return new CompanyResult(audit.companyName(), audit.searchedAt(),
                    sources.stream().mapToInt(SourceResult::newJobs).sum(),
                    sources.stream().mapToInt(SourceResult::duplicateJobs).sum(),
                    sources.stream().mapToInt(SourceResult::filteredOutJobs).sum(),
                    audit.sourcesFailed(), sources);
        }
    }

    /**
     * What one source found for the company: jobsFound = filteredOutJobs + newJobs + duplicateJobs. A failed
     * source found nothing; why it failed isn't shown, as the recorded reason is a technical one.
     */
    public record SourceResult(
            String source,
            SourceAudit.Status status,
            int jobsFound,
            int newJobs,
            int duplicateJobs,
            int filteredOutJobs
    ) {

        public static SourceResult from(final SourceAudit audit) {
            return new SourceResult(audit.source(), audit.status(), audit.jobsRetrieved(), audit.jobsSaved(),
                    audit.duplicateJobsSkipped(), audit.jobsFilteredOut());
        }
    }
}
