package com.systa.service;

import com.systa.model.CompanySearchResult;
import com.systa.model.JobListing;
import com.systa.model.JobPersistResult;
import com.systa.model.UserJobSearchResult;
import com.systa.repository.UserJobSearchResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class JobSearchResultPersistenceService {

    private static final String NOT_SPECIFIED = "Not specified";

    private static final Logger LOGGER = LoggerFactory.getLogger(JobSearchResultPersistenceService.class);

    private final UserJobSearchResultRepository userJobSearchResultRepository;
    private final Clock clock;
    private final Supplier<String> idGenerator;

    @Autowired
    public JobSearchResultPersistenceService(final UserJobSearchResultRepository userJobSearchResultRepository) {
        this(userJobSearchResultRepository, Clock.systemDefaultZone(), () -> UUID.randomUUID().toString());
    }

    JobSearchResultPersistenceService(final UserJobSearchResultRepository userJobSearchResultRepository,
                                       final Clock clock,
                                       final Supplier<String> idGenerator) {
        this.userJobSearchResultRepository = userJobSearchResultRepository;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    /** Saves the jobs the user doesn't already have, and reports which were saved and which were duplicates. */
    public JobPersistResult persist(final String userId, final CompanySearchResult companySearchResult) {
        final LocalDateTime jobRunDateTime = LocalDateTime.now(clock);
        final List<JobListing> savedJobs = new ArrayList<>();
        final List<JobListing> duplicateJobs = new ArrayList<>();
        final List<String> savedResultIds = new ArrayList<>();
        for (final JobListing job : companySearchResult.jobs()) {
            final Optional<String> savedResultId =
                    persistJob(userId, companySearchResult.companyName(), job, jobRunDateTime);
            if (savedResultId.isPresent()) {
                savedJobs.add(job);
                savedResultIds.add(savedResultId.get());
            } else {
                duplicateJobs.add(job);
            }
        }

        return new JobPersistResult(savedJobs, duplicateJobs, savedResultIds);
    }

    // Returns the saved record's id, or empty when the user already has the job.
    private Optional<String> persistJob(final String userId, final String companyName, final JobListing job,
                                        final LocalDateTime jobRunDateTime) {
        final boolean hasReliableJobId = job.jobId() != null && !NOT_SPECIFIED.equalsIgnoreCase(job.jobId());

        if (hasReliableJobId) {
            final boolean alreadyExists = userJobSearchResultRepository
                    .findByUserIdAndCompanyNameAndJob_JobId(userId, companyName, job.jobId())
                    .isPresent();

            if (alreadyExists) {
                logDuplicateJob(userId, companyName, job);
                return Optional.empty();
            }
        }

        logNewJob(userId, companyName, job);
        final String resultId = idGenerator.get();
        userJobSearchResultRepository.save(new UserJobSearchResult(
                resultId,
                userId,
                jobRunDateTime,
                companyName,
                job));
        return Optional.of(resultId);
    }

    private void logNewJob(final String userId, final String companyName, final JobListing job) {
        LOGGER.info("New job added - userId={}, company={}, jobId={}, jobTitle={}",
                userId, companyName, job.jobId(), job.jobTitle());
    }

    private void logDuplicateJob(final String userId, final String companyName, final JobListing job) {
        LOGGER.info("Duplicate job ignored - userId={}, company={}, jobId={}, jobTitle={}",
                userId, companyName, job.jobId(), job.jobTitle());
    }
}
