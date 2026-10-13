package com.systa.exception;

/** A job's posting could not be read into a description. The message is shown to the user. */
public class JobDescriptionUnavailableException extends RuntimeException {

    public JobDescriptionUnavailableException(final String message) {
        super(message);
    }

    public JobDescriptionUnavailableException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
