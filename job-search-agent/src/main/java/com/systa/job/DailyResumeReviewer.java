package com.systa.job;

import com.systa.JobSearchAgentApplication;
import com.systa.config.DailyJobSearchProperties;
import com.systa.model.ResumeReview;
import com.systa.model.ResumeReview.Status;
import com.systa.model.UserResume;
import com.systa.service.ResumeReviewService;
import com.systa.service.ResumeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Part of the daily job: reviews the user's resume against the jobs their search has just saved, so the
 * reviews are ready when they next open the dashboard. Jobs found on earlier days were reviewed then (or
 * can be on request), so only new ones are looked at.
 */
@Component
@Profile(JobSearchAgentApplication.JOB_PROFILE)
@Slf4j
public class DailyResumeReviewer {

    private final ResumeService resumeService;
    private final ResumeReviewService resumeReviewService;
    private final DailyJobSearchProperties properties;

    public DailyResumeReviewer(final ResumeService resumeService,
                               final ResumeReviewService resumeReviewService,
                               final DailyJobSearchProperties properties) {
        this.resumeService = resumeService;
        this.resumeReviewService = resumeReviewService;
        this.properties = properties;
    }

    /**
     * Reviews up to the per-user cap of the new jobs, one at a time, in the order they were saved. A review
     * that fails is recorded on its job by the review service; nothing here is thrown back at the search.
     */
    public void reviewNewJobs(final String userId, final List<String> newJobResultIds) {
        if (newJobResultIds.isEmpty()) {
            return;
        }
        final Optional<UserResume> resume = resumeService.findLatest(userId);
        if (resume.isEmpty()) {
            log.info("No resume uploaded, not reviewing new jobs - userId={}, newJobs={}", userId,
                    newJobResultIds.size());
            return;
        }

        final List<String> toReview = newJobResultIds.stream().limit(properties.reviewsPerUser()).toList();
        int ready = 0;
        int failed = 0;
        for (final String jobResultId : toReview) {
            sleepBeforeReview();
            final Optional<ResumeReview> review = reviewOne(userId, jobResultId, resume.get());
            if (review.isPresent()) {
                if (review.get().status() == Status.READY) {
                    ready++;
                } else {
                    failed++;
                }
            }
        }
        log.info("Resume reviews for new jobs finished - userId={}, newJobs={}, ready={}, failed={}, overCap={}",
                userId, newJobResultIds.size(), ready, failed, newJobResultIds.size() - toReview.size());
    }

    // The review service records its own failures; this catches what it couldn't, such as Mongo being down,
    // so one job doesn't stop the rest.
    private Optional<ResumeReview> reviewOne(final String userId, final String jobResultId,
                                             final UserResume resume) {
        try {
            return resumeReviewService.reviewNewJob(userId, jobResultId, resume);
        } catch (final Exception e) {
            log.error("Resume review for a new job failed - userId={}, jobResultId={}", userId, jobResultId, e);
            return Optional.empty();
        }
    }

    private void sleepBeforeReview() {
        try {
            Thread.sleep(properties.delayBetweenReviews().toMillis());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting between resume reviews", e);
        }
    }
}
