package com.systa.exception;

public class IdentityProviderUnavailableException extends RuntimeException {

    public IdentityProviderUnavailableException(final String userId, final Throwable cause) {
        super("Could not look up the Cognito user for userId: " + userId, cause);
    }
}
