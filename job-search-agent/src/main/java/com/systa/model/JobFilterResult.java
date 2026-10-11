package com.systa.model;

import java.util.List;

/** One company's jobs split by the recency filter into those kept and those dropped. */
public record JobFilterResult(List<JobListing> validJobs, List<FilteredOutJob> filteredOutJobs) {}
