package com.systa.model;

/** A job the LLM returned that the recency filter dropped, and why. */
public record FilteredOutJob(JobListing job, Reason reason) {

    public enum Reason {
        /** datePosted was missing, "Not specified", or not in the dd MMM yyyy format. */
        DATE_POSTED_UNREADABLE,
        /** Posted before the profile's recency window, or dated in the future. */
        OUTSIDE_RECENCY_WINDOW
    }
}
