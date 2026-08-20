package com.systa.service;

import com.systa.model.CompanySearchResult;
import com.systa.model.JobListing;
import com.systa.model.JobSearchResponse;
import com.systa.model.UserJobSearchResult;
import com.systa.repository.UserJobSearchResultRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobSearchResultPersistenceServiceTest {

    private static final String USER_ID = "user-1";
    private static final String COMPANY_NAME = "Acme Corp";
    private static final String GENERATED_ID = "generated-id-1";
    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 8, 18, 12, 0, 0);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(FIXED_NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    @Mock
    private UserJobSearchResultRepository userJobSearchResultRepository;

    @Captor
    private ArgumentCaptor<UserJobSearchResult> savedResultCaptor;

    private JobSearchResultPersistenceService service() {
        return new JobSearchResultPersistenceService(userJobSearchResultRepository, FIXED_CLOCK, () -> GENERATED_ID);
    }

    private JobSearchResultPersistenceService serviceWithSequentialIds() {
        final AtomicInteger counter = new AtomicInteger();
        return new JobSearchResultPersistenceService(
                userJobSearchResultRepository, FIXED_CLOCK, () -> "generated-id-" + counter.incrementAndGet());
    }

    private static JobListing job(final String jobId, final String jobTitle) {
        return new JobListing(jobId, jobTitle, "https://example.com/" + jobId, "London, UK",
                "10 Aug 2026", "20 Aug 2026", "£50,000 - £60,000", "LinkedIn", 90);
    }

    private static JobSearchResponse responseFor(final CompanySearchResult... companies) {
        return new JobSearchResponse(List.of(companies));
    }

    @Test
    void savesNewDocumentForEachJobWhenNoneExist() {
        final JobListing job1 = job("job-1", "Software Engineer");
        final JobListing job2 = job("job-2", "Senior Software Engineer");

        when(userJobSearchResultRepository.findByUserIdAndCompanyNameAndJob_JobId(USER_ID, COMPANY_NAME, "job-1"))
                .thenReturn(Optional.empty());
        when(userJobSearchResultRepository.findByUserIdAndCompanyNameAndJob_JobId(USER_ID, COMPANY_NAME, "job-2"))
                .thenReturn(Optional.empty());

        final JobSearchResponse response = responseFor(new CompanySearchResult(COMPANY_NAME, List.of(job1, job2)));

        serviceWithSequentialIds().persist(USER_ID, response);

        verify(userJobSearchResultRepository, times(2)).save(savedResultCaptor.capture());

        final UserJobSearchResult expectedFirst =
                new UserJobSearchResult("generated-id-1", USER_ID, FIXED_NOW, COMPANY_NAME, job1);
        final UserJobSearchResult expectedSecond =
                new UserJobSearchResult("generated-id-2", USER_ID, FIXED_NOW, COMPANY_NAME, job2);

        assertThat(savedResultCaptor.getAllValues()).containsExactly(expectedFirst, expectedSecond);
    }

    @Test
    void doesNotSaveJobThatAlreadyExistsForUserAndCompanyAndJobId() {
        final JobListing existingJob = job("job-1", "Software Engineer");
        final UserJobSearchResult existing = new UserJobSearchResult(
                "existing-id", USER_ID, LocalDateTime.of(2026, 8, 17, 9, 0, 0), COMPANY_NAME, existingJob);

        when(userJobSearchResultRepository.findByUserIdAndCompanyNameAndJob_JobId(USER_ID, COMPANY_NAME, "job-1"))
                .thenReturn(Optional.of(existing));

        final JobListing duplicateJob = job("job-1", "Software Engineer");
        final JobSearchResponse response = responseFor(new CompanySearchResult(COMPANY_NAME, List.of(duplicateJob)));

        service().persist(USER_ID, response);

        verify(userJobSearchResultRepository, never()).save(any());
    }

    @Test
    void savesOnlyTheNewJobWhenOneOfTwoAlreadyExists() {
        final JobListing existingJob = job("job-1", "Software Engineer");
        final UserJobSearchResult existing = new UserJobSearchResult(
                "existing-id", USER_ID, LocalDateTime.of(2026, 8, 17, 9, 0, 0), COMPANY_NAME, existingJob);

        when(userJobSearchResultRepository.findByUserIdAndCompanyNameAndJob_JobId(USER_ID, COMPANY_NAME, "job-1"))
                .thenReturn(Optional.of(existing));
        when(userJobSearchResultRepository.findByUserIdAndCompanyNameAndJob_JobId(USER_ID, COMPANY_NAME, "job-2"))
                .thenReturn(Optional.empty());

        final JobListing duplicateJob = job("job-1", "Software Engineer");
        final JobListing newJob = job("job-2", "Lead Engineer");
        final JobSearchResponse response =
                responseFor(new CompanySearchResult(COMPANY_NAME, List.of(duplicateJob, newJob)));

        service().persist(USER_ID, response);

        verify(userJobSearchResultRepository, times(1)).save(savedResultCaptor.capture());

        final UserJobSearchResult expected =
                new UserJobSearchResult(GENERATED_ID, USER_ID, FIXED_NOW, COMPANY_NAME, newJob);
        assertThat(savedResultCaptor.getValue()).isEqualTo(expected);
    }

    @Test
    void savesJobsWithMissingOrNotSpecifiedJobIdWithoutCheckingForDuplicates() {
        final JobListing unknownJob = job(null, "Mystery Role");
        final JobListing notSpecifiedJob = job("Not specified", "Another Mystery Role");
        final JobSearchResponse response =
                responseFor(new CompanySearchResult(COMPANY_NAME, List.of(unknownJob, notSpecifiedJob)));

        serviceWithSequentialIds().persist(USER_ID, response);

        verify(userJobSearchResultRepository, never())
                .findByUserIdAndCompanyNameAndJob_JobId(eq(USER_ID), eq(COMPANY_NAME), any());

        verify(userJobSearchResultRepository, times(2)).save(savedResultCaptor.capture());

        final UserJobSearchResult expectedFirst =
                new UserJobSearchResult("generated-id-1", USER_ID, FIXED_NOW, COMPANY_NAME, unknownJob);
        final UserJobSearchResult expectedSecond =
                new UserJobSearchResult("generated-id-2", USER_ID, FIXED_NOW, COMPANY_NAME, notSpecifiedJob);

        assertThat(savedResultCaptor.getAllValues()).containsExactly(expectedFirst, expectedSecond);
    }

    @Test
    void handlesEachCompanyInResponseIndependently() {
        final String otherCompany = "Globex Inc";

        when(userJobSearchResultRepository.findByUserIdAndCompanyNameAndJob_JobId(USER_ID, COMPANY_NAME, "job-10"))
                .thenReturn(Optional.empty());

        final JobListing existingJob = job("job-1", "Software Engineer");
        final UserJobSearchResult existingForOtherCompany = new UserJobSearchResult(
                "other-existing-id", USER_ID, LocalDateTime.of(2026, 8, 17, 9, 0, 0), otherCompany, existingJob);
        when(userJobSearchResultRepository.findByUserIdAndCompanyNameAndJob_JobId(USER_ID, otherCompany, "job-1"))
                .thenReturn(Optional.of(existingForOtherCompany));

        final JobListing newJobForFirstCompany = job("job-10", "New Grad Engineer");
        final JobListing duplicateJobForOtherCompany = job("job-1", "Software Engineer");
        final JobSearchResponse response = responseFor(
                new CompanySearchResult(COMPANY_NAME, List.of(newJobForFirstCompany)),
                new CompanySearchResult(otherCompany, List.of(duplicateJobForOtherCompany)));

        service().persist(USER_ID, response);

        // Globex Inc's only incoming job is a duplicate, so it must never be saved.
        verify(userJobSearchResultRepository, times(1)).save(savedResultCaptor.capture());

        final UserJobSearchResult expected =
                new UserJobSearchResult(GENERATED_ID, USER_ID, FIXED_NOW, COMPANY_NAME, newJobForFirstCompany);
        assertThat(savedResultCaptor.getValue()).isEqualTo(expected);
    }
}
