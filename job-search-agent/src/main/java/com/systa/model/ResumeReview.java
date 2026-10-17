package com.systa.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * A recruiter's-eye review of the user's resume against one saved job: how well it would score with an
 * applicant tracking system, what the job asks for that the resume doesn't say, and what a hiring manager
 * would object to. One per job for each version of the resume, so uploading a new version keeps the
 * reviews of earlier ones; reviewing the same version again (after a failure, say) replaces its review.
 */
@Document(collection = "job_resume_review")
@CompoundIndex(name = "user_job", def = "{'userId': 1, 'jobResultId': 1}")
public record ResumeReview(
        // idFor(jobResultId, resumeVersion): what makes it one review per job per resume version.
        @Id String id,
        // The UserJobSearchResult the review is for.
        String jobResultId,
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

    @PersistenceCreator
    public ResumeReview {
    }

    public ResumeReview(final String jobResultId, final String userId, final Status status, final int resumeVersion,
                        final Integer resumeAtsScore, final List<String> missingKeywords, final List<RedFlag> redFlags,
                        final String failureReason, final Instant requestedAt, final Instant reviewedAt) {
        this(idFor(jobResultId, resumeVersion), jobResultId, userId, status, resumeVersion, resumeAtsScore,
                missingKeywords, redFlags, failureReason, requestedAt, reviewedAt);
    }

    /** The id of the job's review of that version of the user's resume. */
    public static String idFor(final String jobResultId, final int resumeVersion) {
        return jobResultId + ":v" + resumeVersion;
    }

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
