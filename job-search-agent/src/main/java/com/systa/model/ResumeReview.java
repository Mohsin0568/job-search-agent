package com.systa.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * A recruiter's-eye review of the user's resume against one saved job: how well it would score with an
 * applicant tracking system, what the job asks for that the resume doesn't say, and what a hiring manager
 * would object to. One per job; reviewing again replaces it.
 */
@Document(collection = "job_resume_review")
public record ResumeReview(
        // The UserJobSearchResult the review is for.
        @Id String jobResultId,
        String userId,
        Status status,
        // The version of the user's resume that was (or is being) reviewed - always an original upload.
        int resumeVersion,
        // 0 to 100. Null until it is ready.
        Integer resumeAtsScore,
        // At most MAX_MISSING_KEYWORDS; empty when the resume misses nothing. Null until it is ready.
        List<String> missingKeywords,
        // At most MAX_RED_FLAGS; empty when there are none. Null until it is ready.
        List<RedFlag> redFlags,
        // Why the review failed, in words for the user. Null unless it failed.
        String failureReason,
        Instant requestedAt,
        // When it became ready or failed. Null while pending.
        Instant reviewedAt
) {

    public static final int MAX_MISSING_KEYWORDS = 20;
    public static final int MAX_RED_FLAGS = 3;

    public enum Status {
        /** Being reviewed in the background. */
        PENDING,
        READY,
        FAILED
    }

    /** Something a hiring manager would hold against the resume, and why. */
    public record RedFlag(String issue, String explanation) {}

    public ResumeReview ready(final ResumeReviewResult result, final Instant reviewedAt) {
        return new ResumeReview(jobResultId, userId, Status.READY, resumeVersion, result.resumeAtsScore(),
                result.missingKeywords(), result.redFlags(), null, requestedAt, reviewedAt);
    }

    public ResumeReview failed(final String failureReason, final Instant reviewedAt) {
        return new ResumeReview(jobResultId, userId, Status.FAILED, resumeVersion, null, null, null, failureReason,
                requestedAt, reviewedAt);
    }
}
