package com.systa.exception;

import com.systa.model.UserAccess.Status;

/** The signed-in user isn't let in while the app is invite-only: they're on the waitlist, or were shut out. */
public class AccessNotGrantedException extends RuntimeException {

    private final Status status;

    public AccessNotGrantedException(final String userId, final Status status) {
        super("Access not granted - userId=" + userId + ", status=" + status);
        this.status = status;
    }

    public Status status() {
        return status;
    }
}
