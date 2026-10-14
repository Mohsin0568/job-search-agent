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
        // The layout of the PDF. Null on resumes tailored before there was a choice: see templateOrDefault().
        ResumeTemplate template,
        // Where the PDF is in S3, and the name it downloads under. Null until it is ready.
        String s3Key,
        String fileName,
        // Why tailoring failed, in words for the user. Null unless it failed.
        String failureReason,
        Instant requestedAt,
        // When it became ready or failed. Null while pending.
        Instant completedAt,
        // What the LLM wrote, kept so the PDF can be laid out in another template without asking it again.
        // Null until it is ready, and on resumes tailored before it was kept.
        TailoredResumeContent content
) {

    public enum Status {
        /** Being generated in the background. */
        PENDING,
        READY,
        FAILED
    }

    public ResumeTemplate templateOrDefault() {
        return template == null ? ResumeTemplate.DEFAULT : template;
    }

    public TailoredResume ready(final String s3Key, final String fileName, final TailoredResumeContent content,
                                final Instant completedAt) {
        return new TailoredResume(jobResultId, userId, Status.READY, resumeVersion, template, s3Key, fileName, null,
                requestedAt, completedAt, content);
    }

    public TailoredResume failed(final String failureReason, final Instant completedAt) {
        return new TailoredResume(jobResultId, userId, Status.FAILED, resumeVersion, template, null, null,
                failureReason, requestedAt, completedAt, null);
    }

    /** The same tailored resume, laid out again in another template. */
    public TailoredResume relaidOut(final ResumeTemplate template, final String s3Key, final Instant completedAt) {
        return new TailoredResume(jobResultId, userId, status, resumeVersion, template, s3Key, fileName,
                failureReason, requestedAt, completedAt, content);
    }
}
