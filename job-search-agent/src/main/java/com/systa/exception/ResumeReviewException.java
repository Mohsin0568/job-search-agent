package com.systa.exception;

/** The LLM could not review the resume against the job. The message is shown to the user. */
public class ResumeReviewException extends RuntimeException {

    public ResumeReviewException(final String message) {
        super(message);
    }

    public ResumeReviewException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
