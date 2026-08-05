package com.systa.exception;

public class CompanyPreferencesNotSetException extends RuntimeException {

    public CompanyPreferencesNotSetException(final String userId) {
        super("No company preferences set for userId: " + userId);
    }
}
