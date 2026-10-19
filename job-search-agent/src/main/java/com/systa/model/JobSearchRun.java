package com.systa.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One user's part in one daily search: the user's search history, kept for every run. What it found for
 * each company is in the JobSearchAudits that carry its id. (UserJobSearchRun holds only the latest run,
 * for the daily job's same-day rerun check.)
 */
@Document(collection = "job_search_run")
@CompoundIndex(name = "user_started", def = "{'userId': 1, 'startedAt': -1}")
public record JobSearchRun(
        @Id String id,
        String userId,
        // The day (UTC, ISO yyyy-MM-dd) the daily run started on - see UserJobSearchRun.runDate.
        String runDate,
        Instant startedAt,
        Instant finishedAt,
        UserJobSearchRun.Status status,
        int batchesAttempted,
        int batchesFailed
) {}
