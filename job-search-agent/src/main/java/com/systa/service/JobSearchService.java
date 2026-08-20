package com.systa.service;

import com.systa.model.JobSearchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class JobSearchService {

    private final JobSearchLlmService jobSearchLlmService;
    private final JobSearchResultFilterService jobSearchResultFilterService;
    private final JobSearchResultPersistenceService jobSearchResultPersistenceService;

    public JobSearchService(final JobSearchLlmService jobSearchLlmService,
                             final JobSearchResultFilterService jobSearchResultFilterService,
                             final JobSearchResultPersistenceService jobSearchResultPersistenceService) {
        this.jobSearchLlmService = jobSearchLlmService;
        this.jobSearchResultFilterService = jobSearchResultFilterService;
        this.jobSearchResultPersistenceService = jobSearchResultPersistenceService;
    }

    @Async
    public void searchJobs(final String userId) {
        try {
            final JobSearchResponse rawResponse = jobSearchLlmService.searchJobs(userId);
            final JobSearchResponse filteredResponse =
                    jobSearchResultFilterService.filterStaleJobs(userId, rawResponse);
            jobSearchResultPersistenceService.persist(userId, filteredResponse);
        } catch (final Exception e) {
            // Runs fire-and-forget off the request thread, so this is the only place
            // left to surface a failure - the caller has already received its 200.
            log.error("Job search failed - userId={}", userId, e);
        }
    }
}
