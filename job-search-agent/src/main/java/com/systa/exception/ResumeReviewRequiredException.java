package com.systa.exception;

/**
 * The user asked tailoring to fix what the job's resume review found, but there is no finished review of
 * their latest resume to fix it from. The message is shown to them.
 */
public class ResumeReviewRequiredException extends RuntimeException {

    public ResumeReviewRequiredException(final String message) {
        super(message);
    }
}
