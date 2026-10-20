package com.systa.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The body of a request to mark one of the user's saved jobs. */
public record JobStatusUpdate(
        @NotNull UserJobSearchResult.Status status,
        // Optional, and only kept when marking the job NOT_RELEVANT.
        @Size(max = UserJobSearchResult.MAX_NOT_RELEVANT_REASON_LENGTH) String reason
) {}
