package com.systa.service;

import com.systa.exception.JobDescriptionUnavailableException;
import com.systa.exception.ResumeRequiredException;
import com.systa.exception.ResumeReviewException;
import com.systa.model.JobDescription;
import com.systa.model.ResumeReview;
import com.systa.model.ResumeReview.Status;
import com.systa.model.ResumeReviewResult;
import com.systa.model.UserJobSearchResult;
import com.systa.model.UserResume;
import com.systa.repository.ResumeReviewRepository;
import com.systa.repository.UserJobSearchResultRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Reviews the user's latest resume - the original upload, not a tailored one - against one of their saved
 * jobs, as that company's recruiter would. A review takes a while (the posting may need reading first),
 * so it runs in the background: a request returns straight away as PENDING, and the caller asks again
 * until it is READY or FAILED.
 */
@Service
@Slf4j
public class ResumeReviewService {

    // A PENDING review older than this was cut short (the app restarted, say) and may be started again.
    static final Duration PENDING_TIMEOUT = Duration.ofMinutes(10);

    private static final String UNEXPECTED_FAILURE =
            "something went wrong while reviewing your resume, please try again";

    private final UserJobSearchResultRepository userJobSearchResultRepository;
    private final ResumeReviewRepository resumeReviewRepository;
    private final ResumeService resumeService;
    private final JobDescriptionService jobDescriptionService;
    private final ResumeReviewLlmService resumeReviewLlmService;
    private final TaskExecutor taskExecutor;
    private final Clock clock;

    @Autowired
    public ResumeReviewService(final UserJobSearchResultRepository userJobSearchResultRepository,
                               final ResumeReviewRepository resumeReviewRepository,
                               final ResumeService resumeService,
                               final JobDescriptionService jobDescriptionService,
                               final ResumeReviewLlmService resumeReviewLlmService,
                               @Qualifier("applicationTaskExecutor") final TaskExecutor taskExecutor) {
        this(userJobSearchResultRepository, resumeReviewRepository, resumeService, jobDescriptionService,
                resumeReviewLlmService, taskExecutor, Clock.systemUTC());
    }

    ResumeReviewService(final UserJobSearchResultRepository userJobSearchResultRepository,
                        final ResumeReviewRepository resumeReviewRepository,
                        final ResumeService resumeService,
                        final JobDescriptionService jobDescriptionService,
                        final ResumeReviewLlmService resumeReviewLlmService,
                        final TaskExecutor taskExecutor,
                        final Clock clock) {
        this.userJobSearchResultRepository = userJobSearchResultRepository;
        this.resumeReviewRepository = resumeReviewRepository;
        this.resumeService = resumeService;
        this.jobDescriptionService = jobDescriptionService;
        this.resumeReviewLlmService = resumeReviewLlmService;
        this.taskExecutor = taskExecutor;
        this.clock = clock;
    }

    /**
     * Makes sure the job has a review of the user's latest resume: returns the one that is ready or already
     * on its way, and otherwise starts one (again, after a failure or a newer upload).
     *
     * @return empty when the user has no such job
     * @throws ResumeRequiredException when the user hasn't uploaded a resume
     */
    public Optional<ResumeReview> request(final String userId, final String jobResultId) {
        final Optional<UserJobSearchResult> jobResult =
                userJobSearchResultRepository.findByIdAndUserId(jobResultId, userId);
        if (jobResult.isEmpty()) {
            return Optional.empty();
        }
        final UserResume resume = resumeService.findLatest(userId).orElseThrow(() ->
                new ResumeRequiredException("upload your resume before it can be reviewed against a job"));

        final Optional<ResumeReview> existing = resumeReviewRepository.findById(jobResultId);
        if (existing.isPresent() && isCurrent(existing.get(), resume)) {
            return existing;
        }

        final ResumeReview pending = new ResumeReview(jobResultId, userId, Status.PENDING, resume.version(),
                null, null, null, null, now(), null);
        if (existing.isPresent()) {
            resumeReviewRepository.save(pending);
        } else {
            try {
                resumeReviewRepository.insert(pending);
            } catch (final DuplicateKeyException e) {
                // Another request for the same job got there first, and has started the review.
                return resumeReviewRepository.findById(jobResultId);
            }
        }

        taskExecutor.execute(() -> review(pending, jobResult.get(), resume));
        return Optional.of(pending);
    }

    /**
     * Reviews a job the daily search has just saved, and returns once the review is ready or has failed -
     * the daily job runs reviews one at a time, so it waits rather than handing them to the task executor.
     * A job that already has a review (one requested meanwhile, say) is left alone.
     *
     * @param resume the user's latest resume, looked up once by the caller for all of the user's new jobs
     * @return the finished review, or empty when the user has no such job or it already had a review
     */
    public Optional<ResumeReview> reviewNewJob(final String userId, final String jobResultId,
                                               final UserResume resume) {
        final Optional<UserJobSearchResult> jobResult =
                userJobSearchResultRepository.findByIdAndUserId(jobResultId, userId);
        if (jobResult.isEmpty()) {
            log.warn("New job to review not found - userId={}, jobResultId={}", userId, jobResultId);
            return Optional.empty();
        }

        final ResumeReview pending = new ResumeReview(jobResultId, userId, Status.PENDING, resume.version(),
                null, null, null, null, now(), null);
        try {
            resumeReviewRepository.insert(pending);
        } catch (final DuplicateKeyException e) {
            log.info("New job already has a resume review, skipping - userId={}, jobResultId={}", userId,
                    jobResultId);
            return Optional.empty();
        }
        return Optional.of(review(pending, jobResult.get(), resume));
    }

    /** Every review the user has, in whatever state - one per job. */
    public List<ResumeReview> findAll(final String userId) {
        return resumeReviewRepository.findByUserId(userId);
    }

    // Nothing to do when it reviewed the user's latest resume, or is reviewing it right now.
    private boolean isCurrent(final ResumeReview review, final UserResume latestResume) {
        if (review.resumeVersion() != latestResume.version()) {
            return false;
        }
        return switch (review.status()) {
            case READY -> true;
            case PENDING -> review.requestedAt().plus(PENDING_TIMEOUT).isAfter(clock.instant());
            case FAILED -> false;
        };
    }

    // Usually runs in the background. Whatever happens ends up on the record, and is returned for a caller
    // that waited.
    private ResumeReview review(final ResumeReview pending, final UserJobSearchResult jobResult, final UserResume resume) {
        final String userId = pending.userId();
        final String jobResultId = pending.jobResultId();
        ResumeReview outcome;
        try {
            final JobDescription jobDescription = jobDescriptionService.getOrFetch(userId, jobResultId)
                    .orElseThrow(() -> new JobDescriptionUnavailableException("this job is no longer in your results"));
            final ResumeReviewResult result = resumeReviewLlmService.review(userId, resume.extractedText(),
                    jobResult.companyName(), jobResult.job().jobTitle(), jobDescription);
            outcome = pending.ready(result, now());
            log.info("Resume review ready - userId={}, jobResultId={}, resumeVersion={}, score={}", userId,
                    jobResultId, resume.version(), result.resumeAtsScore());
        } catch (final JobDescriptionUnavailableException | ResumeReviewException e) {
            // Both carry a reason written for the user.
            log.warn("Resume review failed - userId={}, jobResultId={}, reason={}", userId, jobResultId,
                    e.getMessage());
            outcome = pending.failed(e.getMessage(), now());
        } catch (final Exception e) {
            log.error("Resume review failed unexpectedly - userId={}, jobResultId={}", userId, jobResultId, e);
            outcome = pending.failed(UNEXPECTED_FAILURE, now());
        }
        record(pending, outcome);
        return outcome;
    }

    private void record(final ResumeReview pending, final ResumeReview outcome) {
        try {
            // If the user asked again meanwhile (after a timeout or a new upload), that request owns
            // the record now and this result is out of date.
            final boolean stillOurs = resumeReviewRepository.findById(pending.jobResultId())
                    .map(current -> current.requestedAt().equals(pending.requestedAt()))
                    .orElse(false);
            if (stillOurs) {
                resumeReviewRepository.save(outcome);
            } else {
                log.info("Discarding a superseded resume review - userId={}, jobResultId={}", pending.userId(),
                        pending.jobResultId());
            }
        } catch (final Exception e) {
            log.error("Could not record the resume review's outcome - userId={}, jobResultId={}, status={}",
                    pending.userId(), pending.jobResultId(), outcome.status(), e);
        }
    }

    // MongoDB keeps dates to the millisecond, so anything finer would not survive a save - and record()
    // compares the requestedAt it holds with the one read back to tell whether the record is still ours.
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
