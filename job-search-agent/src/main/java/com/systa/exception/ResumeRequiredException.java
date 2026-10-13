package com.systa.exception;

/** The user asked for a tailored resume before uploading one to tailor. The message is shown to them. */
public class ResumeRequiredException extends RuntimeException {

    public ResumeRequiredException(final String message) {
        super(message);
    }
}
