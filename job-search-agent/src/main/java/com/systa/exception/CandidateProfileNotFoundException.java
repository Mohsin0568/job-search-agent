package com.systa.exception;

public class CandidateProfileNotFoundException extends RuntimeException {

    public CandidateProfileNotFoundException(final String userId) {
        super("No candidate profile found for userId: " + userId);
    }
}
