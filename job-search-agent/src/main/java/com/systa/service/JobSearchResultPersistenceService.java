package com.systa.service;

import com.systa.model.CompanySearchResult;
import com.systa.model.JobListing;
import com.systa.model.JobSearchResponse;
import com.systa.model.UserJobSearchResult;
import com.systa.repository.UserJobSearchResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
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

    public void persist(final String userId, final JobSearchResponse jobSearchResponse) {
        final LocalDateTime jobRunDateTime = LocalDateTime.now(clock);
        for (final CompanySearchResult company : jobSearchResponse.companies()) {
            for (final JobListing job : company.jobs()) {
                persistJob(userId, company.companyName(), job, jobRunDateTime);
            }
        }
    }

    private void persistJob(final String userId, final String companyName, final JobListing job,
                             final LocalDateTime jobRunDateTime) {
        final boolean hasReliableJobId = job.jobId() != null && !NOT_SPECIFIED.equalsIgnoreCase(job.jobId());

        if (hasReliableJobId) {
            final boolean alreadyExists = userJobSearchResultRepository
                    .findByUserIdAndCompanyNameAndJob_JobId(userId, companyName, job.jobId())
                    .isPresent();

            if (alreadyExists) {
                logDuplicateJob(userId, companyName, job);
                return;
            }
        }

        logNewJob(userId, companyName, job);
        userJobSearchResultRepository.save(new UserJobSearchResult(
                idGenerator.get(),
                userId,
                jobRunDateTime,
                companyName,
                job));
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
