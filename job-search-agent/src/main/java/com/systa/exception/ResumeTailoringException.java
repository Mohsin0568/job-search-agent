package com.systa.exception;

/** The LLM could not turn the resume into a tailored one. The message is shown to the user. */
public class ResumeTailoringException extends RuntimeException {

    public ResumeTailoringException(final String message) {
        super(message);
    }

    public ResumeTailoringException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
