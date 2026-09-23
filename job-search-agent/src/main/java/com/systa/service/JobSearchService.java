package com.systa.service;

import com.systa.exception.CandidateProfileNotFoundException;
import com.systa.exception.CompanyPreferencesNotSetException;
import com.systa.model.CandidateProfile;
import com.systa.model.CompanySearchResult;
import com.systa.model.JobSearchResponse;
import com.systa.repository.CandidateProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class JobSearchService {

    private final JobSearchLlmService jobSearchLlmService;
    private final JobSearchResultFilterService jobSearchResultFilterService;
    private final JobSearchResultPersistenceService jobSearchResultPersistenceService;
    private final CandidateProfileRepository candidateProfileRepository;

    public JobSearchService(final JobSearchLlmService jobSearchLlmService,
                             final JobSearchResultFilterService jobSearchResultFilterService,
                             final JobSearchResultPersistenceService jobSearchResultPersistenceService,
                             final CandidateProfileRepository candidateProfileRepository) {
        this.jobSearchLlmService = jobSearchLlmService;
        this.jobSearchResultFilterService = jobSearchResultFilterService;
        this.jobSearchResultPersistenceService = jobSearchResultPersistenceService;
        this.candidateProfileRepository = candidateProfileRepository;
    }

    @Async
    public void searchJobs(final String userId) {
        try {
            final JobSearchResponse rawResponse = doSearchJobs(userId);
            final JobSearchResponse filteredResponse =
                    jobSearchResultFilterService.filterStaleJobs(userId, rawResponse);
            jobSearchResultPersistenceService.persist(userId, filteredResponse);
        } catch (final Exception e) {
            // Runs fire-and-forget off the request thread, so this is the only place
            // left to surface a failure - the caller has already received its 200.
            log.error("Job search failed - userId={}", userId, e);
        }
    }

    private JobSearchResponse doSearchJobs(final String userId) {
        final CandidateProfile candidateProfile = candidateProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new CandidateProfileNotFoundException(userId));

        if (candidateProfile.companyPreferences() == null || candidateProfile.companyPreferences().isEmpty()) {
            throw new CompanyPreferencesNotSetException(userId);
        }

        final List<CompanySearchResult> companies = new ArrayList<>();
        for (final String company : candidateProfile.companyPreferences()) {
            try {
                companies.add(jobSearchLlmService.searchJobsForCompany(userId, candidateProfile, company));
            } catch (final Exception e) {
                // Isolate failures per company so one bad/rate-limited call doesn't
                // lose results already gathered for the other companies.
                log.error("Job search failed for a single company - userId={}, company={}", userId, company, e);
            }
        }

        return new JobSearchResponse(companies);
    }
}
