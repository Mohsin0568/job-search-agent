package com.systa.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

/** A user's most recent daily search. One document per user, replaced on every run. */
@Document(collection = "user_job_search_run")
public record UserJobSearchRun(
        @Id String userId,
        // The day (UTC, ISO yyyy-MM-dd) the daily run that searched for this user started on - not the day
        // this user's turn came, which can be the next day when a run crosses midnight. Kept as text:
        // Mongo would store a LocalDate as midnight in the JVM's time zone, which shifts the day between machines.
        String runDate,
        Instant startedAt,
        Instant finishedAt,
        Status status,
        int batchesAttempted,
        int batchesFailed
) {

    public enum Status {
        /** Every source batch was searched. */
        SUCCEEDED,
        /** Some source batches failed; the rest were searched and saved. */
        PARTIAL,
        /** Nothing was searched successfully. */
        FAILED,
        /** The profile names no companies, so there was nothing to search. */
        SKIPPED
    }

    /** True when a rerun of the same day's job can leave this user alone. */
    public boolean isDoneFor(final LocalDate date) {
        return date.toString().equals(runDate) && status != Status.FAILED;
    }
}
