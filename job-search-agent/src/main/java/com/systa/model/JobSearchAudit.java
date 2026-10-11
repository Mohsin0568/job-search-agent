package com.systa.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/** What one search of one company's job sources found for a user. One document per user, company and search. */
@Document(collection = "job_search_audit")
public record JobSearchAudit(
        @Id String id,
        String userId,
        String companyName,
        Instant searchedAt,
        int sourcesScanned,
        int sourcesFailed,
        List<SourceAudit> sources
) {

    /**
     * One source's share of the search. The counts follow a job through the pipeline:
     * retrieved = filtered out + valid, and valid = saved + skipped as duplicates.
     */
    public record SourceAudit(
            String source,
            Status status,
            // Why the search of this source failed; null when it was scanned.
            String failureReason,
            // Jobs the LLM returned for this source - after its own recency, role and skills screening.
            int jobsRetrieved,
            int jobsFilteredOut,
            List<FilteredOutJob> filteredOutJobs,
            int validJobs,
            int jobsSaved,
            // Valid jobs not saved because the user already has them from an earlier search.
            int duplicateJobsSkipped
    ) {

        public enum Status {
            SCANNED,
            /** The LLM call covering this source, or saving its results, failed. */
            FAILED
        }

        public static SourceAudit failed(final String source, final String failureReason) {
            return new SourceAudit(source, Status.FAILED, failureReason, 0, 0, List.of(), 0, 0, 0);
        }
    }
}
