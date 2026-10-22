package com.systa.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Locale;

/**
 * Whether one email may use the app while it is invite-only. Edited by hand to manage access:
 * <ul>
 *     <li>invite someone: insert {@code { _id: "jane@example.com", status: "APPROVED" }}</li>
 *     <li>let in someone on the waitlist: set their {@code status} to {@code "APPROVED"}</li>
 *     <li>take access away: set {@code status} to {@code "REVOKED"}</li>
 * </ul>
 * The id is the email in lower case, so an invite and the user's own sign-in meet in one document.
 */
@Document(collection = "user_access")
public record UserAccess(
        @Id String email,
        // The Cognito user (sub) who signed in with this email; null for an invite not yet taken up.
        @Indexed(unique = true, sparse = true) String userId,
        Status status,
        // When the user first signed in with this email; null for an invite not yet taken up.
        Instant firstSignedInAt
) {

    public enum Status {
        /** Signed in without an invite: on the waitlist. */
        PENDING,
        /** Invited, or let in from the waitlist. */
        APPROVED,
        /** Let in once, now shut out. */
        REVOKED
    }

    public boolean approved() {
        return status == Status.APPROVED;
    }

    /** The id an email is stored under: trimmed and in lower case. */
    public static String idFor(final String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
