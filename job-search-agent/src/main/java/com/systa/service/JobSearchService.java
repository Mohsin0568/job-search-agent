package com.systa.service;

import com.systa.model.*;
import com.systa.model.JobSearchAudit.SourceAudit;
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
    private final JobSearchAuditService jobSearchAuditService;
    private final Duration delayBetweenBatchCalls;

    private static final List<JobSource> JOB_SOURCES = List.of(JobSource.values());

    // Batching keeps each call's scraped-content volume under gpt-4.1's org TPM limit -
    // a single call covering all 6 sources was measured at ~36.8k tokens against a 30k/min cap.
    private static final int SOURCES_PER_BATCH = 2;

    private static final List<List<JobSource>> JOB_SOURCE_BATCHES = batchSources();

    // Spaces out consecutive calls so we don't spend the whole TPM bucket in one burst
    // before the provider even has a chance to return a 429 for us to back off on.
    private static final Duration DELAY_BETWEEN_BATCH_CALLS = Duration.ofSeconds(5);

    @Autowired
    public JobSearchService(final JobSearchLlmService jobSearchLlmService,
                             final JobSearchResultFilterService jobSearchResultFilterService,
                             final JobSearchResultPersistenceService jobSearchResultPersistenceService,
                             final JobSearchAuditService jobSearchAuditService) {
        this(jobSearchLlmService, jobSearchResultFilterService, jobSearchResultPersistenceService,
                jobSearchAuditService, DELAY_BETWEEN_BATCH_CALLS);
    }

    JobSearchService(final JobSearchLlmService jobSearchLlmService,
                      final JobSearchResultFilterService jobSearchResultFilterService,
                      final JobSearchResultPersistenceService jobSearchResultPersistenceService,
                      final JobSearchAuditService jobSearchAuditService,
                      final Duration delayBetweenBatchCalls) {
        this.jobSearchLlmService = jobSearchLlmService;
        this.jobSearchResultFilterService = jobSearchResultFilterService;
        this.jobSearchResultPersistenceService = jobSearchResultPersistenceService;
        this.jobSearchAuditService = jobSearchAuditService;
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
            return JobSearchOutcome.NOTHING_SEARCHED;
        }

        int batchesAttempted = 0;
        int batchesFailed = 0;
        final List<String> newJobResultIds = new ArrayList<>();
        for (final String company : candidateProfile.companyPreferences()) {
            final List<SourceAudit> sourceAudits = new ArrayList<>();
            for (final List<JobSource> sourceBatch : JOB_SOURCE_BATCHES) {
                if (batchesAttempted > 0) {
                    sleep(delayBetweenBatchCalls);
                }
                batchesAttempted++;

                final List<String> sourceLabels = sourceBatch.stream().map(JobSource::promptLabel).toList();
                try {
                    final CompanySearchResult batchResult = jobSearchLlmService
                            .searchJobsForCompanyBatch(userId, candidateProfile, company, sourceLabels);
                    sourceAudits.addAll(persistBatchResult(userId, candidateProfile.recencyWindowDays(),
                            sourceBatch, batchResult, newJobResultIds));
                } catch (final Exception e) {
                    // Isolate failures per (company, batch) so one bad/rate-limited call
                    // doesn't stop results already persisted for other batches/companies.
                    batchesFailed++;
                    sourceAudits.addAll(jobSearchAuditService.auditFailedBatch(sourceBatch, e));
                    log.error("Job search failed for a source batch - userId={}, company={}, sources={}",
                            userId, company, sourceLabels, e);
                }
            }
            jobSearchAuditService.record(userId, company, sourceAudits);
        }
        return new JobSearchOutcome(batchesAttempted, batchesFailed, List.copyOf(newJobResultIds));
    }

    private List<SourceAudit> persistBatchResult(final String userId, final Integer recencyWindowDays,
                                                  final List<JobSource> sourceBatch,
                                                  final CompanySearchResult batchResult,
                                                  final List<String> newJobResultIds) {
        final JobFilterResult filterResult = jobSearchResultFilterService.filterStaleJobs(
                userId, recencyWindowDays, sourceBatch, batchResult);
        final CompanySearchResult validJobsSearchResult =
                new CompanySearchResult(batchResult.companyName(), filterResult.validJobs());
        final JobPersistResult persistResult =
                jobSearchResultPersistenceService.persist(userId, validJobsSearchResult);
        newJobResultIds.addAll(persistResult.savedResultIds());
        return jobSearchAuditService.auditScannedBatch(sourceBatch, batchResult.jobs(), filterResult, persistResult);
    }

    private void sleep(final Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting between batch calls", e);
        }
    }

    private static List<List<JobSource>> batchSources() {
        final List<List<JobSource>> batches = new ArrayList<>();
        for (int i = 0; i < JOB_SOURCES.size(); i += SOURCES_PER_BATCH) {
            batches.add(JOB_SOURCES.subList(i, Math.min(i + SOURCES_PER_BATCH, JOB_SOURCES.size())));
        }
        return batches;
    }
}
