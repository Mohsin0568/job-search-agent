package com.systa.exception;

/** Another upload for the same user took the version number this one was about to use. */
public class ResumeUploadConflictException extends RuntimeException {

    public ResumeUploadConflictException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
