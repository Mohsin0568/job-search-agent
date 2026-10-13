package com.systa.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** The user's resume tailored to one saved job. One per job; tailoring again replaces it. */
@Document(collection = "tailored_resume")
public record TailoredResume(
        // The UserJobSearchResult the resume is tailored to.
        @Id String jobResultId,
        String userId,
        Status status,
        // The version of the user's resume it was (or is being) tailored from.
        int resumeVersion,
        // Where the PDF is in S3, and the name it downloads under. Null until it is ready.
        String s3Key,
        String fileName,
        // Why tailoring failed, in words for the user. Null unless it failed.
        String failureReason,
        Instant requestedAt,
        // When it became ready or failed. Null while pending.
        Instant completedAt
) {

    public enum Status {
        /** Being generated in the background. */
        PENDING,
        READY,
        FAILED
    }

    public TailoredResume ready(final String s3Key, final String fileName, final Instant completedAt) {
        return new TailoredResume(jobResultId, userId, Status.READY, resumeVersion, s3Key, fileName, null,
                requestedAt, completedAt);
    }

    public TailoredResume failed(final String failureReason, final Instant completedAt) {
        return new TailoredResume(jobResultId, userId, Status.FAILED, resumeVersion, null, null, failureReason,
                requestedAt, completedAt);
    }
}
