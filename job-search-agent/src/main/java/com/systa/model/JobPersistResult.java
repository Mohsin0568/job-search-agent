package com.systa.model;

import java.util.List;

/**
 * One company's valid jobs split into those newly saved and those the user already had, with the ids of the
 * records saved for the new ones (in the same order as {@code savedJobs}).
 */
public record JobPersistResult(List<JobListing> savedJobs, List<JobListing> duplicateJobs,
                               List<String> savedResultIds) {}
