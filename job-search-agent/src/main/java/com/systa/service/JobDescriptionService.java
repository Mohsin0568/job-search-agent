package com.systa.service;

import com.systa.exception.JobDescriptionUnavailableException;
import com.systa.model.JobDescription;
import com.systa.model.JobListing;
import com.systa.model.JobPosting;
import com.systa.model.UserJobSearchResult;
import com.systa.repository.JobDescriptionRepository;
import com.systa.repository.UserJobSearchResultRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Clock;
import java.util.Optional;

/** The description of a saved job: read from its posting the first time it is asked for, then kept. */
@Service
@Slf4j
public class JobDescriptionService {

    private final UserJobSearchResultRepository userJobSearchResultRepository;
    private final JobDescriptionRepository jobDescriptionRepository;
    private final JobPostingLlmService jobPostingLlmService;
    private final Clock clock;

    @Autowired
    public JobDescriptionService(final UserJobSearchResultRepository userJobSearchResultRepository,
                                  final JobDescriptionRepository jobDescriptionRepository,
                                  final JobPostingLlmService jobPostingLlmService) {
        this(userJobSearchResultRepository, jobDescriptionRepository, jobPostingLlmService, Clock.systemUTC());
    }

    JobDescriptionService(final UserJobSearchResultRepository userJobSearchResultRepository,
                           final JobDescriptionRepository jobDescriptionRepository,
                           final JobPostingLlmService jobPostingLlmService,
                           final Clock clock) {
        this.userJobSearchResultRepository = userJobSearchResultRepository;
        this.jobDescriptionRepository = jobDescriptionRepository;
        this.jobPostingLlmService = jobPostingLlmService;
        this.clock = clock;
    }

    /**
     * The description of one of the user's saved jobs, reading its posting if that hasn't been done yet.
     *
     * @return empty when the user has no such job
     * @throws JobDescriptionUnavailableException when the posting can't be read
     */
    public Optional<JobDescription> getOrFetch(final String userId, final String jobResultId) {
        final Optional<UserJobSearchResult> jobResult =
                userJobSearchResultRepository.findByIdAndUserId(jobResultId, userId);
        if (jobResult.isEmpty()) {
            return Optional.empty();
        }

        final Optional<JobDescription> existing = jobDescriptionRepository.findById(jobResultId);
        if (existing.isPresent()) {
            return existing;
        }

        final JobListing job = jobResult.get().job();
        final String url = postingUrl(job);
        final JobPosting posting = jobPostingLlmService.readPosting(userId, jobResult.get().companyName(),
                job.jobTitle(), url);

        final JobDescription saved = jobDescriptionRepository.save(new JobDescription(jobResultId, userId, url,
                posting.description(), posting.requiredSkills(), clock.instant()));
        log.info("Job description saved - userId={}, jobResultId={}, skills={}", userId, jobResultId,
                saved.requiredSkills().size());
        return Optional.of(saved);
    }

    // The link was written by the LLM from a scraped page: only an ordinary web address is passed on.
    private static String postingUrl(final JobListing job) {
        try {
            final URI uri = URI.create(job.url().strip());
            final boolean isWebAddress = ("https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme())) && uri.getHost() != null;
            if (isWebAddress) {
                return uri.toString();
            }
        } catch (final IllegalArgumentException | NullPointerException _) {
            // Falls through to the same refusal as a link that isn't a web address.
        }
        throw new JobDescriptionUnavailableException("this job has no link to its posting, so we can't read it");
    }
}
