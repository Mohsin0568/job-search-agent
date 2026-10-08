package com.systa.service;

import com.systa.model.CandidateProfile;
import com.systa.model.CompanySearchResult;
import com.systa.model.JobSearchOutcome;
import com.systa.model.JobSearchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class JobSearchService {

    private final JobSearchLlmService jobSearchLlmService;
    private final JobSearchResultFilterService jobSearchResultFilterService;
    private final JobSearchResultPersistenceService jobSearchResultPersistenceService;
    private final Duration delayBetweenBatchCalls;

    private static final List<String> JOB_SOURCES = List.of(
            "The company's official careers/jobs portal",
            "LinkedIn (linkedin.com/jobs)",
            "Indeed UK (uk.indeed.com)",
            "Glassdoor UK (glassdoor.co.uk)",
            "TotalJobs (totaljobs.com)",
            "Reed (reed.co.uk)");

    // Batching keeps each call's scraped-content volume under gpt-4.1's org TPM limit -
    // a single call covering all 6 sources was measured at ~36.8k tokens against a 30k/min cap.
    private static final int SOURCES_PER_BATCH = 2;

    private static final List<List<String>> JOB_SOURCE_BATCHES = batchSources(JOB_SOURCES, SOURCES_PER_BATCH);

    // Spaces out consecutive calls so we don't spend the whole TPM bucket in one burst
    // before the provider even has a chance to return a 429 for us to back off on.
    private static final Duration DELAY_BETWEEN_BATCH_CALLS = Duration.ofSeconds(5);

    @Autowired
    public JobSearchService(final JobSearchLlmService jobSearchLlmService,
                             final JobSearchResultFilterService jobSearchResultFilterService,
                             final JobSearchResultPersistenceService jobSearchResultPersistenceService) {
        this(jobSearchLlmService, jobSearchResultFilterService, jobSearchResultPersistenceService,
                DELAY_BETWEEN_BATCH_CALLS);
    }

    JobSearchService(final JobSearchLlmService jobSearchLlmService,
                      final JobSearchResultFilterService jobSearchResultFilterService,
                      final JobSearchResultPersistenceService jobSearchResultPersistenceService,
                      final Duration delayBetweenBatchCalls) {
        this.jobSearchLlmService = jobSearchLlmService;
        this.jobSearchResultFilterService = jobSearchResultFilterService;
        this.jobSearchResultPersistenceService = jobSearchResultPersistenceService;
        this.delayBetweenBatchCalls = delayBetweenBatchCalls;
    }

    /**
     * Searches every job source for each of the profile's preferred companies, and returns once all of
     * them have been tried. A failed batch is logged and counted rather than thrown, so the caller can
     * tell a clean run from a partial one.
     */
    // Persists each source batch as soon as it comes back, rather than aggregating every
    // company's results in memory first - so a later batch/company hitting a rate limit
    // (even after its own retry) doesn't lose results already fetched.
    public JobSearchOutcome searchJobs(final CandidateProfile candidateProfile) {
        final String userId = candidateProfile.userId();

        if (candidateProfile.companyPreferences() == null || candidateProfile.companyPreferences().isEmpty()) {
            log.warn("No company preferences set, nothing to search - userId={}", userId);
            return new JobSearchOutcome(0, 0);
        }

        int batchesAttempted = 0;
        int batchesFailed = 0;
        for (final String company : candidateProfile.companyPreferences()) {
            for (final List<String> sourceBatch : JOB_SOURCE_BATCHES) {
                if (batchesAttempted > 0) {
                    sleep(delayBetweenBatchCalls);
                }
                batchesAttempted++;

                try {
                    final CompanySearchResult batchResult = jobSearchLlmService
                            .searchJobsForCompanyBatch(userId, candidateProfile, company, sourceBatch);
                    persistBatchResult(userId, candidateProfile.recencyWindowDays(), batchResult);
                } catch (final Exception e) {
                    // Isolate failures per (company, batch) so one bad/rate-limited call
                    // doesn't stop results already persisted for other batches/companies.
                    batchesFailed++;
                    log.error("Job search failed for a source batch - userId={}, company={}, sources={}",
                            userId, company, sourceBatch, e);
                }
            }
        }
        return new JobSearchOutcome(batchesAttempted, batchesFailed);
    }

    private void persistBatchResult(final String userId, final Integer recencyWindowDays,
                                     final CompanySearchResult batchResult) {
        final JobSearchResponse filteredResponse = jobSearchResultFilterService.filterStaleJobs(
                userId, recencyWindowDays, new JobSearchResponse(List.of(batchResult)));
        jobSearchResultPersistenceService.persist(userId, filteredResponse);
    }

    private void sleep(final Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting between batch calls", e);
        }
    }

    private static List<List<String>> batchSources(final List<String> sources, final int batchSize) {
        final List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < sources.size(); i += batchSize) {
            batches.add(sources.subList(i, Math.min(i + batchSize, sources.size())));
        }
        return batches;
    }
}
