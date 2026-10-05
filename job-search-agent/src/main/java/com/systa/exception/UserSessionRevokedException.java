package com.systa.exception;

/** Cognito no longer accepts the user's access token (e.g. after a global sign-out), though its JWT is still valid. */
public class UserSessionRevokedException extends RuntimeException {

    public UserSessionRevokedException(final String userId, final Throwable cause) {
        super("Cognito rejected the access token for userId: " + userId, cause);
    }
}
