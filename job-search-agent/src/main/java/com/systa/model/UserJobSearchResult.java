package com.systa.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * A job the daily search saved for the user. It stays saved whatever the user marks it as, so a later
 * search still sees it as one they already have rather than finding it again.
 */
@Document(collection = "user_job_search_result")
@CompoundIndex(name = "user_status", def = "{'userId': 1, 'status': 1}")
public record UserJobSearchResult(
        @Id String id,
        String userId,
        LocalDateTime jobRunDateTime,
        String companyName,
        JobListing job,
        // Never null: results saved before statuses existed are read as ACTIVE.
        Status status,
        // When the user last changed the status. Null until they do.
        Instant statusChangedAt,
        // Why the user doesn't want the job, if they said. Null unless the status is NOT_RELEVANT.
        String notRelevantReason
) {

    public static final int MAX_NOT_RELEVANT_REASON_LENGTH = 500;

    @PersistenceCreator
    public UserJobSearchResult {
        status = status == null ? Status.ACTIVE : status;
    }

    /** A newly saved job, on the user's dashboard. */
    public UserJobSearchResult(final String id, final String userId, final LocalDateTime jobRunDateTime,
                               final String companyName, final JobListing job) {
        this(id, userId, jobRunDateTime, companyName, job, Status.ACTIVE, null, null);
    }

    public enum Status {
        /** On the user's dashboard. */
        ACTIVE,
        /** The user has applied for it. */
        APPLIED,
        /** The user doesn't want it - from what its resume review found, say. */
        NOT_RELEVANT
    }

    /** Marked with that status; the reason is kept only for NOT_RELEVANT, and a blank one counts as none. */
    public UserJobSearchResult withStatus(final Status newStatus, final String reason, final Instant changedAt) {
        return new UserJobSearchResult(id, userId, jobRunDateTime, companyName, job, newStatus, changedAt,
                newStatus == Status.NOT_RELEVANT ? normalised(reason) : null);
    }

    public UserJobSearchResult withStatus(final Status newStatus, final Instant changedAt) {
        return withStatus(newStatus, null, changedAt);
    }

    private static String normalised(final String reason) {
        return reason == null || reason.isBlank() ? null : reason.strip();
    }
}
