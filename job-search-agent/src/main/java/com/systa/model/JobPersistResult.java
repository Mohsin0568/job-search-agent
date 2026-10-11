package com.systa.model;

import java.util.List;

/** One company's valid jobs split into those newly saved and those the user already had. */
public record JobPersistResult(List<JobListing> savedJobs, List<JobListing> duplicateJobs) {}
