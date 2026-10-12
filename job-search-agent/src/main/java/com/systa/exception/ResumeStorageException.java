package com.systa.exception;

/** S3 could not be reached, or refused the request. */
public class ResumeStorageException extends RuntimeException {

    public ResumeStorageException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
