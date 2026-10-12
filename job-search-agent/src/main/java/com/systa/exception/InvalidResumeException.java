package com.systa.exception;

/** The uploaded file can't be used as a resume. The message is shown to the user. */
public class InvalidResumeException extends RuntimeException {

    public InvalidResumeException(final String message) {
        super(message);
    }

    public InvalidResumeException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
